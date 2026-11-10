package com.kitsugi.animelist.ui.screens.detail

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kitsugi.animelist.data.auth.BangumiApiClient
import com.kitsugi.animelist.data.auth.BangumiAuthStore
import com.kitsugi.animelist.data.auth.ExternalAuthManager
import com.kitsugi.animelist.data.local.TranslationManager
import com.kitsugi.animelist.data.remote.CharacterDetailIdentity
import com.kitsugi.animelist.data.remote.DetailCache
import com.kitsugi.animelist.data.remote.GalleryCategory
import com.kitsugi.animelist.data.remote.GalleryItem
import com.kitsugi.animelist.data.remote.JikanApiClient
import com.kitsugi.animelist.data.remote.KitsugiBangumiDetailClient
import com.kitsugi.animelist.data.remote.KitsugiCharacterDetail
import com.kitsugi.animelist.data.remote.KitsugiMediaMutationsClient
import com.kitsugi.animelist.data.remote.KitsugiPersonImageAggregator
import com.kitsugi.animelist.data.remote.MalJikanMediaSupport
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

class CharacterDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val apiClient = JikanApiClient()
    private val mutationsClient = KitsugiMediaMutationsClient()
    private val translationManager = TranslationManager(context)
    private val settingsDataStore = SettingsDataStore(context)
    private val TAG = "CharacterDetailVM"

    private val _state = MutableStateFlow<CharacterDetailState>(CharacterDetailState.Loading)
    val state: StateFlow<CharacterDetailState> = _state.asStateFlow()

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
    private var lastCharacterId: Int = 0
    private var lastSource: String = ""
    private var lastCharacterName: String? = null
    private var lastIsRealMediaRole: Boolean = false
    private var lastCharacterCacheIdentity: String? = null
    private var bioTranslateJob: kotlinx.coroutines.Job? = null

    /** Çeviri işlerinin nesil sayacı — eski işlerin yüklenme durumunu kapatmasını engeller. */
    @Volatile private var translationGeneration = 0
    private var lastAutoTranslateSetting: Boolean? = null
    private var lastTargetLangSetting: String? = null

    /**
     * Arayüzden (karakter kartından) gelen görsel. Kaynak veride görsel yoksa detay
     * sayfası bunu son çare olarak kullanır — bu sayede hem hayali hem gerçek kişi
     * karakterlerinde sayfa resimsiz açılmaz.
     */
    private var lastHintImageUrl: String? = null

    init {
        viewModelScope.launch {
            settingsDataStore.settingsFlow.collect { settings ->
                val prevAuto = lastAutoTranslateSetting
                val prevTarget = lastTargetLangSetting
                lastAutoTranslateSetting = settings.autoTranslateEnabled
                lastTargetLangSetting = settings.translateTargetLanguage
                if (prevAuto == null && prevTarget == null) return@collect
                if (prevAuto != settings.autoTranslateEnabled || prevTarget != settings.translateTargetLanguage) {
                    val detail = (_state.value as? CharacterDetailState.Success)?.detail ?: return@collect
                    val bio = detail.biography ?: return@collect
                    val expectedKey = currentFetchKey ?: return@collect
                    val isRussian = bio.any { it in '\u0400'..'\u04FF' }
                    if (settings.autoTranslateEnabled || isRussian) {
                        triggerBioTranslationIfNeeded(
                            bio = bio,
                            source = lastSource,
                            characterId = lastCharacterId,
                            cacheIdentity = lastCharacterCacheIdentity,
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
        characterId: Int,
        cacheIdentity: String?,
        expectedFetchKey: String,
        forceRetranslate: Boolean = false
    ) {
        if (bio.isNullOrBlank()) return
        if (!forceRetranslate) {
            val cachedTranslation = DetailCache.getTranslation(
                "bio_char", source, characterId, cacheIdentity, sourceText = bio
            )
            // Önbellekteki çeviri kaynak metinle eşdeğerse (ör. eski sürümde Türkçe metin
            // Türkçeye "çevrilip" kaydedilmişse) çeviri yok sayılır.
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
                    // Kaynak metin zaten hedef dildeyse çeviri üretilmemiştir: "Orijinal /
                    // Çeviri" düğmesi boşuna görünmesin, eski yanlış kayıt da silinsin.
                    if (com.kitsugi.animelist.utils.translationMatchesSource(bio, tr)) {
                        DetailCache.removeTranslation("bio_char", source, characterId, cacheIdentity)
                        _translatedBio.value = null
                        return@launch
                    }
                    DetailCache.putTranslation(
                        "bio_char", source, characterId, tr, cacheIdentity, sourceText = bio
                    )
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
        val detail = (_state.value as? CharacterDetailState.Success)?.detail ?: return
        val bio = detail.biography ?: return
        val expectedFetchKey = currentFetchKey ?: return
        triggerBioTranslationIfNeeded(
            bio = bio,
            source = lastSource,
            characterId = lastCharacterId,
            cacheIdentity = lastCharacterCacheIdentity,
            expectedFetchKey = expectedFetchKey,
            forceRetranslate = true
        )
    }

    fun loadCharacter(characterId: Int, source: String, name: String? = null, hintImageUrl: String? = null, isRealMediaRole: Boolean = false) {
        val newKey = CharacterDetailIdentity.fetchKey(source, characterId, name, isRealMediaRole)
        val currentHintImageUrl = hintImageUrl?.takeIf { it.isNotBlank() }
        val cacheIdentity = CharacterDetailIdentity.cacheDiscriminator(source, name, isRealMediaRole)
        if (newKey == currentFetchKey) {
            lastHintImageUrl = currentHintImageUrl ?: lastHintImageUrl
            Log.d(TAG, "loadCharacter: Cache hit for key=$newKey — skipping")
            return
        }

        Log.d(TAG, "loadCharacter: New key=$newKey (was $currentFetchKey)")
        currentFetchKey = newKey
        cancelBioTranslation()
        _isRefreshing.value = false
        galleryTracker.reset()
        lastCharacterId = characterId
        lastSource = source
        lastCharacterName = name
        lastIsRealMediaRole = isRealMediaRole
        lastHintImageUrl = currentHintImageUrl
        lastCharacterCacheIdentity = cacheIdentity

        val cachedCharacterDetail = DetailCache.getCharacterDetail(source, characterId, cacheIdentity)
            ?.takeIf { CharacterDetailIdentity.matchesExpectedDetail(source, name, isRealMediaRole, it) }
        // Önbellekteki çeviri ancak **farklı** bir metinse gösterilir; aynı metnin biçim
        // farkıyla dönmesi (Türkçe→Türkçe) "Çeviri" düğmesi açmamalı.
        val cachedBioTranslation = cachedCharacterDetail?.biography?.let { bio ->
            DetailCache.getTranslation("bio_char", source, characterId, cacheIdentity, sourceText = bio)
                ?.takeIf { tr -> !com.kitsugi.animelist.utils.translationMatchesSource(bio, tr) }
        }

        _state.value = if (cachedCharacterDetail != null) CharacterDetailState.Success(cachedCharacterDetail) else CharacterDetailState.Loading
        _isFavourite.value = cachedCharacterDetail?.isFavourite ?: false
        _translatedBio.value = cachedBioTranslation

        // Yeni karaktere geçerken önceki role ait görsel/galeri ipucu ekranda kalmasın.
        _galleryItems.value = if (currentHintImageUrl != null) {
            listOf(
                GalleryItem(
                    url = currentHintImageUrl,
                    source = friendlySourceOf(source),
                    category = GalleryCategory.CHARACTER,
                    description = name
                )
            )
        } else {
            emptyList()
        }

        viewModelScope.launch {
            fetchCharacterDetail(
                characterId = characterId,
                source = source,
                name = name,
                isRealMediaRole = isRealMediaRole,
                expectedFetchKey = newKey,
                hintImageUrl = currentHintImageUrl
            )
        }
    }

    fun retry() {
        val characterId = lastCharacterId
        val source = lastSource
        val name = lastCharacterName
        val expectedFetchKey = currentFetchKey ?: return
        val isRealMediaRole = lastIsRealMediaRole
        val hintImageUrl = lastHintImageUrl
        if (characterId > 0 && source.isNotEmpty()) {
            _state.value = CharacterDetailState.Loading
            _translatedBio.value = null
            viewModelScope.launch {
                fetchCharacterDetail(
                    characterId = characterId,
                    source = source,
                    name = name,
                    force = true,
                    isRealMediaRole = isRealMediaRole,
                    expectedFetchKey = expectedFetchKey,
                    hintImageUrl = hintImageUrl
                )
            }
        }
    }

    fun forceRefresh() {
        val characterId = lastCharacterId
        val source = lastSource
        val name = lastCharacterName
        val expectedFetchKey = currentFetchKey ?: return
        val isRealMediaRole = lastIsRealMediaRole
        val hintImageUrl = lastHintImageUrl
        if (characterId > 0 && source.isNotEmpty()) {
            _isRefreshing.value = true
            viewModelScope.launch {
                try {
                    fetchCharacterDetail(
                        characterId = characterId,
                        source = source,
                        name = name,
                        force = true,
                        isRealMediaRole = isRealMediaRole,
                        expectedFetchKey = expectedFetchKey,
                        hintImageUrl = hintImageUrl
                    )
                } finally {
                    if (currentFetchKey == expectedFetchKey) _isRefreshing.value = false
                }
            }
        }
    }

    /** Detay + galeri işinin tamamını izler; galeri bitmeden [galleryLoading] false olmaz. */
    private suspend fun fetchCharacterDetail(
        characterId: Int,
        source: String,
        name: String? = null,
        force: Boolean = false,
        isRealMediaRole: Boolean = false,
        expectedFetchKey: String,
        hintImageUrl: String?
    ) {
        val token = galleryTracker.begin()
        try {
            fetchCharacterDetailInternal(
                characterId = characterId,
                source = source,
                name = name,
                force = force,
                isRealMediaRole = isRealMediaRole,
                expectedFetchKey = expectedFetchKey,
                hintImageUrl = hintImageUrl
            )
        } finally {
            galleryTracker.end(token)
        }
    }

    private suspend fun fetchCharacterDetailInternal(
        characterId: Int,
        source: String,
        name: String? = null,
        force: Boolean = false,
        isRealMediaRole: Boolean = false,
        expectedFetchKey: String,
        hintImageUrl: String?
    ) {
        if (currentFetchKey != expectedFetchKey) return
        val cacheIdentity = CharacterDetailIdentity.cacheDiscriminator(source, name, isRealMediaRole)
        if (force) {
            DetailCache.removeCharacterDetail(source, characterId, cacheIdentity)
            DetailCache.removeTranslation("bio_char", source, characterId, cacheIdentity)
        }
        val cached = DetailCache.getCharacterDetail(source, characterId, cacheIdentity)
            ?.takeIf { CharacterDetailIdentity.matchesExpectedDetail(source, name, isRealMediaRole, it) }
        val detail = if (cached != null && !force) {
            cached
        } else {
            runCatching {
                withContext(Dispatchers.IO) {
                    apiClient.fetchCharacterDetail(
                        source,
                        characterId,
                        name,
                        fallbackImageUrl = hintImageUrl,
                        isRealMediaRole = isRealMediaRole
                    )
                }
            }.onFailure { err ->
                val msg = when (err) {
                    is RateLimitException -> "Hız limitine takıldık. Lütfen birkaç saniye bekleyip tekrar deneyin."
                    is ResourceNotFoundException -> "Bu karakter bulunamadı."
                    is java.io.IOException -> "Ağ bağlantısı hatası. Lütfen bağlantınızı kontrol edin."
                    else -> "Karakter detayları yüklenemedi."
                }
                Log.e(TAG, "fetchCharacterDetail error: ${err.message}", err)
                if (currentFetchKey == expectedFetchKey && _state.value !is CharacterDetailState.Success) {
                    _state.value = CharacterDetailState.Error(msg, com.kitsugi.animelist.data.platform.PlatformFailureRegistry.reportAndResolve(source, err))
                }
            }.getOrNull()?.takeIf {
                CharacterDetailIdentity.matchesExpectedDetail(source, name, isRealMediaRole, it)
            }
        }

        if (currentFetchKey != expectedFetchKey) return
        if (detail != null) {
            DetailCache.putCharacterDetail(source, characterId, detail, cacheIdentity)
            _state.value = CharacterDetailState.Success(detail)
            _isFavourite.value = detail.isFavourite

            // Biyografi çevirisini galeri ve ek ağ çağrılarını beklemeden HEMEN başlat!
            triggerBioTranslationIfNeeded(
                bio = detail.biography,
                source = source,
                characterId = characterId,
                cacheIdentity = cacheIdentity,
                expectedFetchKey = expectedFetchKey,
                forceRetranslate = force
            )

            if (isBangumiSource(source)) {
                refreshBangumiFavourite(detail.id)
                warmBangumiCharacterTitles(detail)
            }

            // Build gallery from imageUrl + Jikan /pictures. Obsolete responses cannot replace
            // the gallery belonging to a more recently opened role.
            buildCharacterGallery(
                characterId = characterId,
                source = source,
                detail = detail,
                requestedName = name,
                isRealMediaRole = isRealMediaRole,
                expectedFetchKey = expectedFetchKey
            )
        } else if (currentFetchKey == expectedFetchKey && _state.value !is CharacterDetailState.Success) {
            _state.value = CharacterDetailState.Error(
                KitsugiStrings.get(R.string.karakter_detaylari_yuklenemedi),
                com.kitsugi.animelist.data.platform.PlatformFailureRegistry.currentOrUnknown(source),
            )
        }
    }

    /** Kaynak anahtarı → kullanıcıya gösterilen ad. */
    private fun friendlySourceOf(source: String): String = when (source.lowercase().trim()) {
        "kitsu" -> "Kitsu"
        "anilist" -> "AniList"
        "shikimori" -> "Shikimori"
        "simkl" -> "Simkl"
        "tmdb" -> "TMDB"
        "bangumi", "bgm" -> "Bangumi"
        "mal", "jikan" -> "MyAnimeList"
        else -> "Karakter"
    }

    /**
     * Karakter galerisi: birincil görsel (sayfanın açıldığı kaynak) + **tüm kaynaklardan**
     * toplanan ek görseller (bkz. [KitsugiPersonImageAggregator]).
     *
     * Kimlik çözüm kuralları:
     *  - Jikan/MAL kaynağı: kimlik zaten MAL kimliğidir.
     *  - Shikimori: karakter kimliği MAL kimliğiyle aynıdır (Shikimori MAL'i aynalar).
     *  - Diğer kaynaklar (ör. Kitsu): detay Jikan dalına düştüyse detayın kimliği MAL
     *    kimliğidir; aksi hâlde Jikan sorgusu yapılmaz (404 riski yok).
     *  - AniList: detayın `aniListId`'si veya (kaynak anilist ise) sayfa kimliği.
     */
    private suspend fun buildCharacterGallery(
        characterId: Int,
        source: String,
        detail: KitsugiCharacterDetail,
        requestedName: String?,
        isRealMediaRole: Boolean = false,
        expectedFetchKey: String
    ) {
        if (currentFetchKey != expectedFetchKey) return
        val mainImageUrl = detail.imageUrl
        val canonical = MalJikanMediaSupport.canonicalSource(source)

        val malId = when {
            canonical == "jikan" -> characterId.takeIf { it > 0 }
            // Shikimori karakter/kişi kimliği MAL kimliğiyle aynıdır (Shikimori MAL'i aynalar).
            canonical == "shikimori" -> detail.id.takeIf { it > 0 }
            // Kitsu dalı Jikan detayına düşmüşse detayın kimliği gerçek MAL kimliğidir.
            // NOT: AniList/Bangumi/TMDB kimlikleri MAL uzayında DEĞİLDİR — detaydan MAL
            // kimliği çıkarımı yalnızca bu güvenli dalda yapılır (yanlış galeri riski yok).
            canonical == "kitsu" -> detail
                .takeIf { MalJikanMediaSupport.isMalSource(it.source) && it.id != characterId }
                ?.id?.takeIf { it > 0 }
            else -> null
        }
        val aniListId = detail.aniListId?.takeIf { it > 0 }
            ?: if (canonical == "anilist") characterId.takeIf { it > 0 } else null

        val names = buildList {
            add(detail.name)
            detail.name.substringBefore("/").trim().takeIf { it.isNotEmpty() }?.let(::add)
            requestedName?.let(::add)
            requestedName?.substringBefore("/")?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
            detail.nativeName?.let(::add)
            detail.romanizedName?.let(::add)
            detail.englishName?.let(::add)
            addAll(detail.alternativeNames)
        }.filter { it.isNotBlank() }.distinct()

        val tmdbEnabled = runCatching { settingsDataStore.settingsFlow.first() }
            .getOrNull()?.tmdbEnabled ?: true

        val friendlySource = friendlySourceOf(source)
        val description = detail.name.ifBlank { requestedName.orEmpty() }

        val aggregated = runCatching {
            KitsugiPersonImageAggregator.aggregate(
                kind = KitsugiPersonImageAggregator.PersonKind.CHARACTER,
                source = source,
                id = characterId,
                malId = malId,
                aniListId = aniListId,
                names = names,
                tmdbEnabled = tmdbEnabled,
                // Karakterin kimliği (MAL/AniList) biliniyorsa ya da sayfa adı doğrulanmış bir
                // TMDB rolünden açıldıysa tek kelimeli adlar ("Nobita", "Doraemon") da aday
                // kabul edilir; katı kural bu karakterlerin galerisini tamamen boş bırakıyordu.
                allowSingleTokenNames = aniListId != null || malId != null ||
                    canonical == "tmdb" || !requestedName.isNullOrBlank()
            )
        }.getOrDefault(emptyList())

        val isVoiceActorPhoto: (String?) -> Boolean = { candidateUrl ->
            !isRealMediaRole && detail.voiceActors.any { va ->
                DetailCache.isSameImageResource(candidateUrl, va.imageUrl)
            }
        }
        val safeMainImageUrl = mainImageUrl?.takeUnless(isVoiceActorPhoto)

        val items = buildList {
            if (!safeMainImageUrl.isNullOrBlank()) {
                add(GalleryItem(url = safeMainImageUrl, source = friendlySource, category = GalleryCategory.CHARACTER, description = description))
            }
            for (img in aggregated) {
                if (img.url != safeMainImageUrl && img.url.isNotBlank() && !isVoiceActorPhoto(img.url)) {
                    add(GalleryItem(url = img.url, source = img.source, category = GalleryCategory.CHARACTER, description = description))
                }
            }
        }.distinctBy { it.url }

        val bestPortrait = items.firstOrNull()?.url
        if (!bestPortrait.isNullOrBlank() && !isVoiceActorPhoto(bestPortrait)) {
            if (!isRealMediaRole) {
                val portraitTargets = listOfNotNull(
                    detail.name,
                    detail.name.substringBefore("/").trim().takeIf { it.isNotEmpty() },
                    requestedName,
                    requestedName?.substringBefore("/")?.trim()?.takeIf { it.isNotEmpty() }
                ).filter { it.isNotBlank() }.distinct()
                portraitTargets.forEach { alias -> DetailCache.propagateCharacterPortrait(alias, bestPortrait) }
            }
            if ((detail.imageUrl.isNullOrBlank() || isVoiceActorPhoto(detail.imageUrl)) && currentFetchKey == expectedFetchKey) {
                val upgraded = detail.copy(imageUrl = bestPortrait)
                _state.value = CharacterDetailState.Success(upgraded)
                DetailCache.putCharacterDetail(source, characterId, upgraded, lastCharacterCacheIdentity)
            }
        }

        if (currentFetchKey == expectedFetchKey) _galleryItems.value = items
    }

    /**
     * Wiki sekmesi karakter sayfasını çözdüğünde ana detayda eksik olan portre veya biyografiyi
     * kurgusal karakter verisiyle tamamlar ve önbellekteki karakter listesine yayar.
     */
    fun onWikiCharacterPageResolved(page: com.kitsugi.animelist.data.fandom.FandomCharacterPage) {
        if (lastIsRealMediaRole) return
        val currentState = _state.value as? CharacterDetailState.Success ?: return
        var detail = currentState.detail
        val expectedKey = currentFetchKey ?: return

        val isVoiceActorPhoto: (String?) -> Boolean = { candidateUrl ->
            detail.voiceActors.any { va -> DetailCache.isSameImageResource(candidateUrl, va.imageUrl) }
        }
        val wikiImages = page.infobox?.images.orEmpty().filter { it.url.isNotBlank() && !isVoiceActorPhoto(it.url) }
        val firstWikiPortrait = wikiImages.firstOrNull()?.url
        var updated = false

        if (!firstWikiPortrait.isNullOrBlank()) {
            val portraitTargets = listOfNotNull(
                detail.name,
                detail.name.substringBefore("/").trim().takeIf { it.isNotEmpty() },
                lastCharacterName,
                lastCharacterName?.substringBefore("/")?.trim()?.takeIf { it.isNotEmpty() }
            ).filter { it.isNotBlank() }.distinct()
            portraitTargets.forEach { alias -> DetailCache.propagateCharacterPortrait(alias, firstWikiPortrait) }

            if (detail.imageUrl.isNullOrBlank() || isVoiceActorPhoto(detail.imageUrl)) {
                detail = detail.copy(imageUrl = firstWikiPortrait)
                updated = true
            }
            val currentGallery = _galleryItems.value
            val wikiSourceLabel = page.siteName ?: "Wiki"
            val newGalleryItems = (currentGallery + wikiImages.map { img ->
                GalleryItem(
                    url = img.url,
                    source = wikiSourceLabel,
                    category = GalleryCategory.CHARACTER,
                    description = img.caption ?: detail.name
                )
            }).distinctBy { it.url }
            if (newGalleryItems.size != currentGallery.size) {
                _galleryItems.value = newGalleryItems
            }
        }

        val currentBio = detail.biography?.trim().orEmpty()
        val isPlaceholderBio = currentBio.isEmpty() ||
            currentBio == "Bu kurgusal karakter için ek biyografi bilgisi bulunmuyor."
        val wikiBio = page.description?.trim().orEmpty()
        if (isPlaceholderBio && wikiBio.isNotEmpty()) {
            detail = detail.copy(biography = wikiBio)
            updated = true
            triggerBioTranslationIfNeeded(
                bio = wikiBio,
                source = lastSource,
                characterId = lastCharacterId,
                cacheIdentity = lastCharacterCacheIdentity,
                expectedFetchKey = expectedKey
            )
        }

        if (updated) {
            _state.value = CharacterDetailState.Success(detail)
            DetailCache.putCharacterDetail(lastSource, lastCharacterId, detail, lastCharacterCacheIdentity)
        }
    }

    /**
     * AniList karakter favori toggle — giriş yapılmış ve aniListId biliniyorsa çalışır.
     * Anında UI güncellemesi yapar, arka planda mutasyon çalışır.
     */
    fun toggleFavourite() {
        if (isBangumiSource(lastSource)) {
            toggleBangumiFavourite()
            return
        }
        ExternalAuthManager.getAniListToken(context) ?: return
        val currentState = _state.value as? CharacterDetailState.Success ?: return
        val detail = currentState.detail
        val targetId = detail.aniListId ?: if (lastSource.lowercase() == "anilist") detail.id else null
        if (targetId == null || targetId <= 0) return

        val newFav = !_isFavourite.value
        val source = lastSource
        val characterId = lastCharacterId
        val cacheIdentity = lastCharacterCacheIdentity
        val expectedFetchKey = currentFetchKey
        _isFavourite.value = newFav
        val updated = detail.copy(isFavourite = newFav)
        _state.value = CharacterDetailState.Success(updated)
        DetailCache.putCharacterDetail(source, characterId, updated, cacheIdentity)

        viewModelScope.launch(Dispatchers.IO) {
            val ok = mutationsClient.toggleFavourite("character", targetId)
            if (!ok && currentFetchKey == expectedFetchKey) {
                // rollback only if the same role page is still visible
                _isFavourite.value = !newFav
                val rolled = detail.copy(isFavourite = !newFav)
                _state.value = CharacterDetailState.Success(rolled)
                DetailCache.putCharacterDetail(source, characterId, rolled, cacheIdentity)
            }
        }
    }

    private fun isBangumiSource(source: String): Boolean {
        val key = source.trim().lowercase()
        return key == "bangumi" || key == "bgm"
    }

    /**
     * "Yapımlar" listesindeki CJK başlıklar için arka plan çözümü: senkron zenginleştirmenin
     * süre bütçesine sığmayan satırları çözer, kalıcı önbelleğe yazar ve değişiklik varsa
     * ekranı günceller. İkinci ziyarette liste tamamen Latin başlıkla açılır.
     */
    private fun warmBangumiCharacterTitles(detail: KitsugiCharacterDetail) {
        viewModelScope.launch {
            val updated = withContext(Dispatchers.IO) {
                runCatching {
                    KitsugiBangumiDetailClient.enrichCharacterDetailNames(detail, background = true)
                }
                    .getOrNull()
            } ?: return@launch
            if (updated == detail) return@launch
            if (lastCharacterId != detail.id || !isBangumiSource(lastSource)) return@launch
            // Eşzamanlı favori güncellemesinin isFavourite alanını ezmesini engelle.
            val merged = updated.copy(
                isFavourite = (_state.value as? CharacterDetailState.Success)?.detail?.isFavourite
                    ?: updated.isFavourite
            )
            DetailCache.putCharacterDetail(lastSource, detail.id, merged)
            _state.value = CharacterDetailState.Success(merged)
        }
    }

    /** Bangumi karakterinde favori durumunu token sahibinin koleksiyonundan okur (bağlıysa). */
    private fun refreshBangumiFavourite(characterId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!BangumiAuthStore.isConnected(context)) return@launch
            val token = BangumiAuthStore.getValidToken(context) ?: return@launch
            val username = BangumiAuthStore.getUsername(context)?.takeIf { it.isNotBlank() } ?: "-"
            val favourite = runCatching {
                BangumiApiClient.isCharacterFavourite(token, username, characterId)
            }.getOrNull() ?: return@launch
            _isFavourite.value = favourite
            val current = (_state.value as? CharacterDetailState.Success)?.detail ?: return@launch
            if (current.id != characterId) return@launch
            val updated = current.copy(isFavourite = favourite)
            _state.value = CharacterDetailState.Success(updated)
            DetailCache.putCharacterDetail(lastSource, lastCharacterId, updated)
        }
    }

    /**
     * Bangumi karakter favorisi: `POST` / `DELETE /v0/characters/{id}/collect`.
     * İyimser güncelleme yapılır; istek başarısız olursa önceki duruma dönülür.
     */
    private fun toggleBangumiFavourite() {
        if (!BangumiAuthStore.isConnected(context)) return
        val currentState = _state.value as? CharacterDetailState.Success ?: return
        val detail = currentState.detail
        val newFav = !_isFavourite.value
        _isFavourite.value = newFav
        val updated = detail.copy(isFavourite = newFav)
        _state.value = CharacterDetailState.Success(updated)
        DetailCache.putCharacterDetail(lastSource, lastCharacterId, updated)

        viewModelScope.launch(Dispatchers.IO) {
            val ok = runCatching {
                val token = BangumiAuthStore.getValidToken(context) ?: return@runCatching false
                BangumiApiClient.setCharacterFavourite(token, detail.id, newFav)
                true
            }.getOrDefault(false)
            if (!ok) {
                _isFavourite.value = !newFav
                val rolled = detail.copy(isFavourite = !newFav)
                _state.value = CharacterDetailState.Success(rolled)
                DetailCache.putCharacterDetail(lastSource, lastCharacterId, rolled)
            }
        }
    }
}
