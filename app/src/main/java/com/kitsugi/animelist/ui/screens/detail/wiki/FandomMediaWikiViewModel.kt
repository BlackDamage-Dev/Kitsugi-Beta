package com.kitsugi.animelist.ui.screens.detail.wiki

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kitsugi.animelist.data.fandom.FandomCharacterPage
import com.kitsugi.animelist.data.fandom.FandomArticlePage
import com.kitsugi.animelist.data.fandom.FandomFullPage
import com.kitsugi.animelist.data.fandom.FandomRelatedPage
import com.kitsugi.animelist.data.fandom.FandomWikiResolver
import java.util.LinkedHashSet
import com.kitsugi.animelist.data.fandom.FandomSectionContent
import com.kitsugi.animelist.data.fandom.FandomGuideGroup
import com.kitsugi.animelist.data.fandom.FandomGuideItem
import com.kitsugi.animelist.data.fandom.FandomMediaQuery
import com.kitsugi.animelist.data.fandom.FandomNameVariants
import com.kitsugi.animelist.data.fandom.FandomPageBlurb
import com.kitsugi.animelist.data.fandom.FandomRepository
import com.kitsugi.animelist.data.fandom.FandomSearchHit
import com.kitsugi.animelist.data.fandom.FandomUrls
import com.kitsugi.animelist.data.fandom.FandomWikiPrefs
import com.kitsugi.animelist.data.fandom.FandomWikiRef
import com.kitsugi.animelist.data.fandom.FandomWorldGuide
import com.kitsugi.animelist.data.local.TranslationManager
import com.kitsugi.animelist.data.settings.SettingsDataStore
import com.kitsugi.animelist.utils.KitsugiTranslatePrefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Yapım (anime/manga/dizi/film) detayındaki "Wiki" sekmesinin durumu.
 *
 * Karakter sekmesiyle aynı ilkeler: sekme sayfayla birlikte doğar (iskelet gösterir),
 * wiki eşleşmesi arka planda çözülür; seri özeti, evren rehberi ve arama sonuçları
 * sekme göründükten sonra aşamalı çekilir. Rehber ve açıklamalar kalıcı önbellektedir.
 */
class FandomMediaWikiViewModel(application: Application) : AndroidViewModel(application) {

    sealed interface UiState {
        data object Idle : UiState
        data object Loading : UiState
        data class Ready(
            val ref: FandomWikiRef,
            val mediaKey: String,
            val resolutionKey: String = mediaKey,
            val generation: Long = 0L,
            val availableSources: List<FandomWikiRef> = listOf(ref),
            val activeSourceIndex: Int = 0
        ) : UiState
        data object Unavailable : UiState
    }

    sealed interface ArticleState {
        data object Idle : ArticleState
        data object Loading : ArticleState
        data class Ready(val page: FandomCharacterPage) : ArticleState
        data object Unavailable : ArticleState
    }

    sealed interface GuideState {
        data object Idle : GuideState
        data object Loading : GuideState
        data class Ready(val guide: FandomWorldGuide) : GuideState
        data object Failed : GuideState

        /**
         * Bu kaynakta "evren rehberi" kavramı yok: Vikipedi'de Fandom'daki gibi kategori
         * şeması (Karakterler/Kuruluşlar/Bölümler…) bulunmaz; `Category:Karakterler` gibi
         * genel kategoriler yapımla ilgisiz çipler üretirdi. Kart hiç çizilmez.
         */
        data object Unsupported : GuideState
    }

    /**
     * "Devamını göster" akışının durumu (aynı anda tek grup yüklenir).
     * [Failed] yalnızca ilgili grubun düğmesinde "tekrar dene" ipucu gösterir; çipler kalır.
     */
    sealed interface GuideMore {
        data object Idle : GuideMore
        data class Loading(val group: FandomGuideGroup) : GuideMore
        data class Failed(val group: FandomGuideGroup) : GuideMore
    }

    sealed interface BlurbState {
        data object Idle : BlurbState
        data object Loading : BlurbState
        data class Ready(val blurb: FandomPageBlurb?) : BlurbState
        data object Failed : BlurbState
    }

    sealed interface SearchState {
        data object Idle : SearchState
        data object Loading : SearchState
        data class Ready(val hits: List<FandomSearchHit>) : SearchState
        data object Failed : SearchState
    }

    sealed interface SelectedState {
        data object Closed : SelectedState
        data object Loading : SelectedState
        data class Ready(val title: String, val article: FandomArticlePage) : SelectedState
        data class Failed(val title: String) : SelectedState
    }

    sealed interface ArticleSectionState {
        data object Loading : ArticleSectionState
        data class Loaded(val content: FandomSectionContent) : ArticleSectionState
        data object Failed : ArticleSectionState
    }

    /** Uygulama içi wiki okuyucu: mevcut sayfa + geri yığını + ilgili sayfalar. */
    sealed interface ReaderState {
        data object Closed : ReaderState
        data object Loading : ReaderState
        data class Ready(
            val page: FandomFullPage,
            /** Başlangıç özeti (kısa açıklama) ve sonrasında yüklenen bölümler. */
            val sections: Map<Int, FandomSectionContent>,
            /** İçerikteki bağlantılardan ve "Ayrıca bakınız"dan derlenen ilgili sayfalar. */
            val related: List<FandomRelatedPage>,
            val loadingSections: Set<Int> = emptySet(),
            val failedSections: Set<Int> = emptySet()
        ) : ReaderState
        data class Failed(val title: String) : ReaderState
    }


    companion object {
        private const val TAG = "FandomMediaWikiVM"
        const val KEY_SUMMARY = "media:desc"
        fun itemKey(title: String): String = "item:$title"
        fun articleSectionKey(title: String, index: Int): String = "item-sec:$index:$title"
        fun articleInfoboxKey(title: String): String = "item-info:$title"
        fun articleInfoboxFieldKey(title: String, index: Int): String = "item-if:$index:$title"
        private const val SEARCH_DEBOUNCE_MS = 350L

        /** "İlgili maddeler" listesine girmemesi gereken ansiklopedi iskelet sayfaları. */
        private val ENCYCLOPEDIA_BOILERPLATE_TITLES = setOf(
            "anasayfa", "main page", "wikipedia", "vikipedi", "portal", "help", "yardim"
        )
        private val ENCYCLOPEDIA_BOILERPLATE_PREFIXES = listOf(
            "vikipedi ", "wikipedia ", "yardim ", "help ", "portal ", "sablon ", "template ",
            "dosya ", "file ", "kategori ", "category ", "ozel ", "special ", "taslak "
        )
    }

    private val appContext = application.applicationContext
    private val translationManager by lazy { TranslationManager(appContext) }
    private val settingsDataStore by lazy { SettingsDataStore(appContext) }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _article = MutableStateFlow<ArticleState>(ArticleState.Idle)
    val article: StateFlow<ArticleState> = _article.asStateFlow()

    private val _articleSections = MutableStateFlow<Map<Int, FandomCharacterWikiViewModel.SectionState>>(emptyMap())
    val articleSections: StateFlow<Map<Int, FandomCharacterWikiViewModel.SectionState>> = _articleSections.asStateFlow()

    private val _guide = MutableStateFlow<GuideState>(GuideState.Idle)
    val guide: StateFlow<GuideState> = _guide.asStateFlow()

    private val _guideMore = MutableStateFlow<GuideMore>(GuideMore.Idle)
    val guideMore: StateFlow<GuideMore> = _guideMore.asStateFlow()

    private val _summary = MutableStateFlow<BlurbState>(BlurbState.Idle)
    val summary: StateFlow<BlurbState> = _summary.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchState = MutableStateFlow<SearchState>(SearchState.Idle)
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

    private val _selected = MutableStateFlow<SelectedState>(SelectedState.Closed)
    val selected: StateFlow<SelectedState> = _selected.asStateFlow()

    private val _selectedSections = MutableStateFlow<Map<Int, ArticleSectionState>>(emptyMap())
    val selectedSections: StateFlow<Map<Int, ArticleSectionState>> = _selectedSections.asStateFlow()

    private val _reader = MutableStateFlow<ReaderState>(ReaderState.Closed)
    val reader: StateFlow<ReaderState> = _reader.asStateFlow()

    /** Ana sayfa için toplanmış "İlgili wiki sayfaları" kartları. */
    private val _relatedPages = MutableStateFlow<List<FandomRelatedPage>>(emptyList())
    val relatedPages: StateFlow<List<FandomRelatedPage>> = _relatedPages.asStateFlow()
    private var relatedJob: Job? = null

    // Son reddedilen wiki: kullanıcı "Hayır, yanlış"a bastıktan sonra sekmenin en üstünde
    // "Geri al" kartı olarak gösterilir. Farklı bir yapıma geçildiğinde düşürülür.
    private var lastRejectedBindingKey: String? = null
    private val _lastRejected = MutableStateFlow<FandomWikiRef?>(null)
    val lastRejected: StateFlow<FandomWikiRef?> = _lastRejected.asStateFlow()

    private var readerJob: Job? = null
    private val readerSectionJobs = HashMap<Int, Job>()
    private var readerRelatedJob: Job? = null
    private var readerWebMode = true
    private val readerBrowserController = FandomReaderBrowserController()
    private val _readerBrowser = MutableStateFlow(FandomReaderBrowserState.Empty)
    val readerBrowser: StateFlow<FandomReaderBrowserState> = _readerBrowser.asStateFlow()


    private val _translations = MutableStateFlow<Map<String, String>>(emptyMap())
    val translations: StateFlow<Map<String, String>> = _translations.asStateFlow()

    private val _translating = MutableStateFlow<Set<String>>(emptySet())
    val translating: StateFlow<Set<String>> = _translating.asStateFlow()

    /**
     * Her çeviri kaydının hangi KAYNAK METİNDEN üretildiği (anahtar → metin).
     *
     * Çeviriler eskiden yalnızca anahtarla ("desc", "sec:0"…) tutuluyordu; wiki kaynağı
     * değiştiğinde (Fandom → Vikipedi ya da Vikipedi TR → EN) aynı anahtar başka bir maddeyi
     * işaret ettiği hâlde eski çeviri ekranda kalıyordu. Sonuç: "Orijinal / Çeviri" düğmesi
     * içeriği TAMAMEN farklı bir metne çeviriyordu. Kaynak metin eşleşmiyorsa çeviri geçersiz
     * sayılır ([pruneStaleTranslations]).
     */
    private val translationBases = HashMap<String, String>()

    /** Kaynaklar arasında gidip gelince çeviri ve Orijinal/Çeviri seçimi kaybolmasın. */
    private data class SourceTranslationSnapshot(
        val translations: Map<String, String>,
        val originalKeys: Set<String>,
        val bases: Map<String, String>
    )

    private val sourceTranslationSnapshots = HashMap<String, SourceTranslationSnapshot>()

    private fun sourceTranslationKey(ref: FandomWikiRef): String =
        "${ref.wikiId}|${ref.pageTitle.orEmpty()}"

    /**
     * Orijinal metne dönülmüş anahtarlar ([KEY_SUMMARY], [itemKey], [FandomCharacterWikiViewModel.KEY_DESCRIPTION], vb.).
     * Çeviri bellekte kalır; geçiş düğmesiyle tekrar gösterilebilir. Bu anahtarlar için
     * otomatik çeviri yeniden çalışıp üstüne yazmaz.
     */
    private val _originalKeys = MutableStateFlow<Set<String>>(emptySet())
    val originalKeys: StateFlow<Set<String>> = _originalKeys.asStateFlow()

    /**
     * Ayarlardaki çeviri uygulaması tercihi ("GOOGLE", "DEEPL", "TRANSLATE_YOU", "DEFAULT").
     * Wiki çeviri düğmeleri detay sayfasındaki açıklama düğmesiyle aynı davrandığı için
     * (dokununca harici çeviri uygulaması açılır) bu tercih UI'ya akıtılır.
     */
    private val _preferredTranslator = MutableStateFlow("DEFAULT")
    val preferredTranslator: StateFlow<String> = _preferredTranslator.asStateFlow()

    val prefs: StateFlow<FandomWikiPrefs.State> = FandomWikiPrefs.stateFlow

    private var boundKey: String? = null
    private var bindingGeneration = 0L
    private var lastQuery: FandomMediaQuery? = null
    private var resolveJob: Job? = null
    private var articleJob: Job? = null
    private var articleGeneration = 0L
    private var guideJob: Job? = null
    private var guideMoreJob: Job? = null
    private var summaryJob: Job? = null
    private var searchJob: Job? = null
    private var selectedJob: Job? = null
    private val selectedSectionJobs = HashMap<Int, Job>()
    private val translationJobs = HashMap<String, Job>()
    private var translationGeneration = 0L

    init {
        FandomWikiPrefs.ensureLoaded(appContext)
        viewModelScope.launch {
            FandomWikiPrefs.stateFlow
                .map { Triple(it.enabled, it.nameFallback, it.preferTurkish) }
                .distinctUntilChanged()
                .drop(1)
                .collect { rebind() }
        }
        // Detay sayfalarındaki açıklama çevirisiyle aynı tepkisellik: sekme açıkken
        // otomatik çeviri açılırsa yüklü metinler çevrilir, kapanırsa orijinale dönülür,
        // hedef dil değişince çeviriler tazelenir. Harici çeviri tercihi de buradan akar.
        viewModelScope.launch {
            var previous: Triple<Boolean, String, String>? = null
            settingsDataStore.settingsFlow
                .map { Triple(it.autoTranslateEnabled, it.translateTargetLanguage, it.preferredTranslator) }
                .distinctUntilChanged()
                .collect { current ->
                    _preferredTranslator.value = current.third
                    val previousState = previous
                    previous = current
                    if (previousState == null) return@collect
                    val autoChanged = previousState.first != current.first
                    val targetChanged = previousState.second != current.second
                    when {
                        autoChanged && !current.first -> {
                            cancelTranslations()
                            _translations.value = emptyMap()
                        }
                        (autoChanged && current.first) || (targetChanged && current.first) -> {
                            retranslateLoadedTexts(force = targetChanged)
                        }
                        else -> Unit
                    }
                }
        }
    }

    /** Sayfa açıldığında / kimlikler netleştiğinde çağrılır; aynı sorgu için tekrar çözmez. */
    fun bind(query: FandomMediaQuery?) {
        lastQuery = query
        val prefsState = FandomWikiPrefs.state(appContext)
        val key = if (query == null || !prefsState.enabled) {
            "none"
        } else {
            "${query.resolutionKey()}|${prefsState.enabled}|${prefsState.nameFallback}|${prefsState.preferTurkish}"
        }
        if (key == boundKey) return
        boundKey = key
        if (lastRejectedBindingKey != boundKey) {
            lastRejectedBindingKey = null
            _lastRejected.value = null
        }
        val generation = ++bindingGeneration
        resolveJob?.cancel()
        articleJob?.cancel()
        articleGeneration += 1
        guideJob?.cancel()
        guideMoreJob?.cancel()
        _guideMore.value = GuideMore.Idle
        summaryJob?.cancel()
        searchJob?.cancel()
        selectedJob?.cancel()
        cancelSelectedSectionJobs()
        cancelTranslations()
        readerJob?.cancel()
        readerSectionJobs.values.forEach { it.cancel() }
        readerSectionJobs.clear()
        readerRelatedJob?.cancel()
        relatedJob?.cancel()
        readerWebMode = true
        readerBrowserController.reset()
        _readerBrowser.value = FandomReaderBrowserState.Empty
        _relatedPages.value = emptyList()
        _reader.value = ReaderState.Closed
        _article.value = ArticleState.Idle
        _articleSections.value = emptyMap()
        _selectedSections.value = emptyMap()
        _translations.value = emptyMap()
        translationBases.clear()
        sourceTranslationSnapshots.clear()
        _originalKeys.value = emptySet()
        _guide.value = GuideState.Idle
        _summary.value = BlurbState.Idle
        _searchQuery.value = ""
        _searchState.value = SearchState.Idle
        _selected.value = SelectedState.Closed

        if (query == null || !prefsState.enabled) {
            _state.value = UiState.Unavailable
            return
        }
        _state.value = UiState.Loading
        resolveJob = viewModelScope.launch {
            val refs = try {
                FandomRepository.resolveAllMediaWikis(appContext, query, allowNameFallback = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Wiki çözümlenemedi: ${e.message}")
                emptyList()
            }
            if (generation != bindingGeneration) return@launch
            val mediaKey = query.cacheKey()
            val primary = refs.firstOrNull()
            _state.value = if (primary != null && mediaKey != null) {
                UiState.Ready(
                    ref = primary,
                    mediaKey = mediaKey,
                    resolutionKey = query.resolutionKey() ?: mediaKey,
                    generation = generation,
                    availableSources = refs,
                    activeSourceIndex = 0
                )
            } else {
                UiState.Unavailable
            }
        }
    }

    /**
     * Farklı bir wiki kaynağına geçiş yapar (Fandom ↔ Vikipedi; Vikipedi TR ↔ EN).
     *
     * Çeviri görünümü kaynak başına saklanır. Böylece Fandom'da "Orijinal", Vikipedi'de
     * "Çeviri" seçilebilir; geri dönünce hem seçim hem hazır çeviri bellekte kalır. Kaynaklar
     * aynı `desc`/`sec:0` anahtarlarını kullandığı için aktif kaynağın anlık görüntüsü dışında
     * hiçbir kayıt UI'ya verilmez; başka maddenin çevirisi ekrana sızamaz.
     */
    fun switchSource(index: Int) {
        val current = _state.value as? UiState.Ready ?: return
        if (index !in current.availableSources.indices || index == current.activeSourceIndex) return
        val newRef = current.availableSources[index]
        // Aktif kaynağın çevirisini ve görünüm tercihini, anahtar çakışmalarından yalıtılmış
        // kaynak kovasına al. Devam eden işlerin kısmi sonucu kaynak değişiminden sonra yazamaz.
        sourceTranslationSnapshots[sourceTranslationKey(current.ref)] = SourceTranslationSnapshot(
            translations = _translations.value,
            originalKeys = _originalKeys.value,
            bases = translationBases.toMap()
        )
        _state.value = current.copy(
            ref = newRef,
            activeSourceIndex = index
        )
        cancelTranslations()
        val restored = sourceTranslationSnapshots[sourceTranslationKey(newRef)]
        _translations.value = restored?.translations.orEmpty()
        _originalKeys.value = restored?.originalKeys.orEmpty()
        translationBases.clear()
        restored?.bases?.let(translationBases::putAll)
        articleJob?.cancel()
        articleGeneration += 1
        _article.value = ArticleState.Idle
        _articleSections.value = emptyMap()
        summaryJob?.cancel()
        _summary.value = BlurbState.Idle
        guideJob?.cancel()
        guideMoreJob?.cancel()
        _guideMore.value = GuideMore.Idle
        _guide.value = GuideState.Idle
        relatedJob?.cancel()
        _relatedPages.value = emptyList()
        searchJob?.cancel()
        _searchQuery.value = ""
        _searchState.value = SearchState.Idle
        selectedJob?.cancel()
        cancelSelectedSectionJobs()
        cancelArticleItemTranslations()
        _selected.value = SelectedState.Closed
        _selectedSections.value = emptyMap()
        ensureContentLoaded()
    }

    /** Orijinal / çeviri geçişi: bu anahtar için orijinal metnin gösterilip gösterilmeyeceğini değiştirir. */
    fun toggleOriginal(key: String) {
        val wasOriginal = key in _originalKeys.value
        _originalKeys.update { if (key in it) it - key else it + key }
        // If Original was selected while a structured section was still translating,
        // returning to Translation should immediately resume/request that section.
        if (wasOriginal && !_translations.value.hasWikiTranslation(key)) {
            viewModelScope.launch {
                if (isAutoTranslateEnabled()) translate(key)
            }
        }
    }

    private fun rebind() {
        boundKey = null
        bind(lastQuery)
    }

    /** "Tekrar dene": eski (olumsuz dahil) wiki önbelleği silinir, çözümleme baştan yapılır. */
    fun retry() {
        val query = lastQuery
        val wikiId = (_state.value as? UiState.Ready)?.ref?.wikiId
        viewModelScope.launch {
            if (query != null) {
                try {
                    FandomRepository.forgetMediaWiki(appContext, query)
                    if (wikiId != null) FandomRepository.forgetMediaArticle(appContext, query, wikiId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Önbellek temizlenemedi: ${e.message}")
                }
            }
            rebind()
        }
    }

    /**
     * Sekme görünür olduğunda çağrılır: yapım maddesi + (kaynağa göre) seri özeti ve evren rehberi.
     *
     * Kaynak Vikipedi olduğunda "seri özeti" (ana sayfa özeti) ve "evren rehberi" (kategori
     * çipleri) KASTEN yüklenmez: Vikipedi ana sayfası yapımın özeti değildir ve orada Fandom
     * tarzı bir kategori şeması yoktur. İlgili maddeler de ana sayfa yerine yapımın kendi
     * maddesinden türetilir.
     */
    fun ensureContentLoaded() {
        val ready = _state.value as? UiState.Ready ?: return
        val query = lastQuery ?: return
        val wikipediaSource = FandomUrls.isWikipedia(ready.ref.wikiId)
        if (_article.value == ArticleState.Idle) {
            val articleRequestGeneration = articleGeneration
            _article.value = ArticleState.Loading
            articleJob = viewModelScope.launch {
                val page = try {
                    FandomRepository.resolveMediaArticle(appContext, query, ready.ref)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Yapım wiki maddesi yüklenemedi: ${e.message}")
                    null
                }
                if (articleRequestGeneration != articleGeneration || !isCurrent(ready)) return@launch
                _article.value = if (page != null) ArticleState.Ready(page) else ArticleState.Unavailable
                // Madde metni değişti: önceki metne bağlı çeviriler düşer.
                pruneStaleTranslations()
                if (!page?.description.isNullOrBlank() && isAutoTranslateEnabled()) {
                    translate(FandomCharacterWikiViewModel.KEY_DESCRIPTION)
                }
                if (page != null && FandomUrls.isWikipedia(ready.ref.wikiId)) {
                    loadRelatedPagesForWikipedia(ready)
                }
            }
        }
        if (_summary.value == BlurbState.Idle) {
            if (wikipediaSource) {
                // Vikipedi'de "seri özeti" kartı boş kalır (madde özeti zaten madde kartında).
                _summary.value = BlurbState.Ready(null)
            } else {
                _summary.value = BlurbState.Loading
                summaryJob = viewModelScope.launch {
                    val blurb = try {
                        FandomRepository.loadMainPageBlurb(appContext, ready.ref.wikiId)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Seri özeti yüklenemedi: ${e.message}")
                        null
                    }
                    if (!isCurrent(ready)) return@launch
                    _summary.value = BlurbState.Ready(blurb)
                    pruneStaleTranslations()
                    if (!blurb?.description.isNullOrBlank()) maybeAutoTranslate(KEY_SUMMARY)
                }
            }
        }
        if (_guide.value == GuideState.Idle) {
            if (wikipediaSource) {
                _guide.value = GuideState.Unsupported
            } else {
                _guide.value = GuideState.Loading
                guideJob = viewModelScope.launch {
                    val guide = try {
                        FandomRepository.loadWorldGuide(appContext, ready.ref.wikiId)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Evren rehberi yüklenemedi: ${e.message}")
                        null
                    }
                    if (!isCurrent(ready)) return@launch
                    _guide.value = if (guide != null) GuideState.Ready(guide) else GuideState.Failed
                }
            }
        }
        if (!wikipediaSource && _relatedPages.value.isEmpty()) {
            relatedJob?.cancel()
            relatedJob = viewModelScope.launch {
                val pages = try {
                    val mainPage = FandomWikiResolver.siteInfoCached(ready.ref.wikiId)?.mainPage ?: return@launch
                    val candidateTitles = LinkedHashSet<String>()
                    for (idx in listOf(0, 1)) {
                        val sec = FandomRepository.loadSection(appContext, ready.ref.wikiId, mainPage, idx)
                        if (sec != null) candidateTitles += sec.links
                    }
                    if (candidateTitles.isNotEmpty()) {
                        FandomRepository.loadRelatedPages(appContext, ready.ref.wikiId, candidateTitles.take(30))
                    } else emptyList()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "İlgili wiki sayfaları yüklenemedi: ${e.message}")
                    emptyList()
                }
                if (!isCurrent(ready)) return@launch
                val main = FandomWikiResolver.siteInfoCached(ready.ref.wikiId)?.mainPage.orEmpty()
                _relatedPages.value = pages.filter { !it.title.equals(main, ignoreCase = true) }.take(20)
            }
        }
    }

    /**
     * Vikipedi kaynağında "İlgili maddeler": kaynak, wiki'nin ANA SAYFASI değil yapımın KENDİ
     * maddesidir. Anasayfa bağlantıları günün seçkin maddeleridir ("Tarım, Hukuk, Ahlat,
     * Türkçe"…) ve yapımla hiçbir ilgisi yoktu.
     *
     * İlk denemede maddenin giriş + ilk bölüm bağlantıları olduğu gibi alınıyordu; bu da
     * `{{Animanga lisans}}` gibi şablonların bilgi kutusunda ürettiği ÜLKE/BÖLGE
     * bağlantılarını ("Amerika Birleşik Devletleri", "Kanada"…) ve API'nin pageid
     * sıralamasını şeride taşıyordu. Artık adaylar gövde metninden çıkarılıp sıklık +
     * kısa tanım süzgeciyle önem sırasına dizilir ([FandomRepository.loadWikipediaRelatedPages]).
     */
    private fun loadRelatedPagesForWikipedia(ready: UiState.Ready) {
        val article = (_article.value as? ArticleState.Ready)?.page ?: return
        if (_relatedPages.value.isNotEmpty()) return
        relatedJob?.cancel()
        relatedJob = viewModelScope.launch {
            val pages = try {
                // Adaylar artık bilgi kutusu/navbox/otorite kontrolü değil GÖVDE metninden
                // çıkarılır ve geçiş sıklığına göre önem sırasına dizilir; API'nin pageid
                // sıralaması ve 20'li devam sınırı FandomApi.pagePreviews içinde çözülür.
                FandomRepository.loadWikipediaRelatedPages(
                    context = appContext,
                    wikiId = article.wikiId,
                    pageTitle = article.pageTitle,
                    selfTitles = listOfNotNull(article.pageTitle, ready.ref.pageTitle),
                    workTitles = lastQuery?.fallbackTitles.orEmpty()
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "İlgili maddeler yüklenemedi (${article.pageTitle}): ${e.message}")
                emptyList()
            }
            if (!isCurrent(ready)) return@launch
            val current = (_article.value as? ArticleState.Ready)?.page ?: return@launch
            if (current.wikiId != article.wikiId || current.pageTitle != article.pageTitle) return@launch
            _relatedPages.value = pages
                .filter { !it.title.equals(article.pageTitle, ignoreCase = true) }
                .filterNot { isEncyclopediaBoilerplatePage(it.title) }
                .take(20)
        }
    }

    /** Ansiklopedi iskelet sayfaları (ana sayfa, portal, yardım…) "ilgili madde" değildir. */
    private fun isEncyclopediaBoilerplatePage(title: String): Boolean {
        val normalized = FandomNameVariants.normalizeForCompare(title)
        if (normalized.isEmpty()) return true
        if (normalized in ENCYCLOPEDIA_BOILERPLATE_TITLES) return true
        return ENCYCLOPEDIA_BOILERPLATE_PREFIXES.any { normalized.startsWith(it) }
    }

    /** Retry fetching the complete article without discarding the already-resolved wiki. */
    fun retryArticle() {
        val ready = _state.value as? UiState.Ready ?: return
        val query = lastQuery ?: return
        articleJob?.cancel()
        articleGeneration += 1
        val oldArticleTranslationKeys = translationJobs.keys.filter(::isArticleTranslationKey).toSet()
        oldArticleTranslationKeys.forEach { key -> translationJobs.remove(key)?.cancel() }
        _translating.update { it - oldArticleTranslationKeys }
        relatedJob?.cancel()
        _relatedPages.value = emptyList()
        _article.value = ArticleState.Loading
        _articleSections.value = emptyMap()
        translationBases.keys.filter(::isArticleTranslationKey).forEach { translationBases.remove(it) }
        _translations.update { map -> map.filterKeys { key -> !isArticleTranslationKey(key) } }
        viewModelScope.launch {
            try {
                FandomRepository.forgetMediaArticle(appContext, query, ready.ref.wikiId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Madde önbelleği temizlenemedi: ${e.message}")
            }
            if (!isCurrent(ready)) return@launch
            _article.value = ArticleState.Idle
            ensureContentLoaded()
        }
    }

    fun loadArticleSection(index: Int) {
        val page = (_article.value as? ArticleState.Ready)?.page ?: return
        when (_articleSections.value[index]) {
            is FandomCharacterWikiViewModel.SectionState.Loading,
            is FandomCharacterWikiViewModel.SectionState.Loaded -> return
            else -> Unit
        }
        val generation = articleGeneration
        _articleSections.update { it + (index to FandomCharacterWikiViewModel.SectionState.Loading) }
        viewModelScope.launch {
            val content = try {
                FandomRepository.loadSection(
                    appContext,
                    page.wikiId,
                    page.pageTitle,
                    index,
                    page.sections.firstOrNull { it.index == index }?.title
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Wiki bölümü yüklenemedi ($index): ${e.message}")
                null
            }
            if (generation != articleGeneration || !isCurrentArticle(page)) return@launch
            val loaded = if (content != null) {
                FandomCharacterWikiViewModel.SectionState.Loaded(content)
            } else {
                FandomCharacterWikiViewModel.SectionState.Failed
            }
            _articleSections.update { it + (index to loaded) }
            pruneStaleTranslations()
            if (content != null && !content.isEmpty() && isAutoTranslateEnabled()) {
                translate(FandomCharacterWikiViewModel.sectionKey(index))
            }
        }
    }

    /** User-confirmed wiki matches are remembered across visits. */
    fun confirmMatch() {
        val ready = _state.value as? UiState.Ready ?: return
        com.kitsugi.animelist.data.fandom.FandomUserChoices.acceptWiki(
            appContext,
            ready.mediaKey,
            ready.ref.wikiId
        )
        val acceptedRef = ready.ref.copy(
            source = com.kitsugi.animelist.data.fandom.FandomMatchSource.USER,
            confidence = com.kitsugi.animelist.data.fandom.FandomConfidence.CERTAIN
        )
        _state.value = ready.copy(ref = acceptedRef)
        val page = (_article.value as? ArticleState.Ready)?.page
        if (page != null) {
            _article.value = ArticleState.Ready(
                page.copy(
                    matchSource = com.kitsugi.animelist.data.fandom.FandomMatchSource.USER,
                    confidence = com.kitsugi.animelist.data.fandom.FandomConfidence.CERTAIN,
                    wikiSource = com.kitsugi.animelist.data.fandom.FandomMatchSource.USER,
                    wikiConfidence = com.kitsugi.animelist.data.fandom.FandomConfidence.CERTAIN
                )
            )
        } else if (_article.value is ArticleState.Loading) {
            articleJob?.cancel()
            _article.value = ArticleState.Idle
            ensureContentLoaded()
        }
    }

    fun retryGuide() {
        _guide.value = GuideState.Idle
        _guideMore.value = GuideMore.Idle
        ensureContentLoaded()
    }

    /**
     * "Devamını göster": ilgili grubun kategorisinde sonraki sayfayı çeker ve çiplere ekler.
     *
     * Jeton `null` ise (kategori tükendi) hiç istek atılmaz. Aynı anda tek grup yüklenir;
     * yükleme sürerken gelen dokunuşlar yok sayılır. Hata olursa mevcut çipler korunur,
     * düğme "tekrar dene" durumuna geçer.
     */
    fun loadMoreGuide(group: FandomGuideGroup) {
        val ready = _state.value as? UiState.Ready ?: return
        val content = (_guide.value as? GuideState.Ready)
            ?.guide?.groups?.firstOrNull { it.group == group } ?: return
        val token = content.nextToken ?: return
        if (_guideMore.value is GuideMore.Loading) return
        _guideMore.value = GuideMore.Loading(group)
        guideMoreJob = viewModelScope.launch {
            val page = try {
                FandomRepository.loadAllRemainingGuideItems(
                    wikiId = ready.ref.wikiId,
                    category = content.category,
                    startToken = token
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Rehber tüm sayfalar yüklenemedi (${content.category}): ${e.message}")
                null
            }
            if (!isCurrent(ready)) return@launch
            val current = (_guide.value as? GuideState.Ready)?.guide
            if (page == null || current == null) {
                _guideMore.value = GuideMore.Failed(group)
                return@launch
            }
            val merged = current.groups.map { item ->
                if (item.group != group) {
                    item
                } else {
                    item.copy(
                        items = (item.items + page.items.map(::FandomGuideItem))
                            .distinctBy { it.title },
                        nextToken = page.nextToken
                    )
                }
            }
            _guide.value = GuideState.Ready(current.copy(groups = merged))
            _guideMore.value = GuideMore.Idle
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            _searchState.value = SearchState.Idle
            return
        }
        val ready = _state.value as? UiState.Ready ?: return
        _searchState.value = SearchState.Loading
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            val hits = try {
                FandomRepository.searchWiki(appContext, ready.ref.wikiId, trimmed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Arama başarısız: ${e.message}")
                null
            }
            if (!isCurrent(ready)) return@launch
            _searchState.value = if (hits != null) SearchState.Ready(hits) else SearchState.Failed
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _searchQuery.value = ""
        _searchState.value = SearchState.Idle
    }

    fun openItem(title: String) {
        val ready = _state.value as? UiState.Ready ?: return
        selectedJob?.cancel()
        cancelSelectedSectionJobs()
        cancelArticleItemTranslations()
        _selectedSections.value = emptyMap()
        _selected.value = SelectedState.Loading
        selectedJob = viewModelScope.launch {
            val article = try {
                FandomRepository.loadArticle(appContext, ready.ref.wikiId, title)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Wiki makalesi yüklenemedi ($title): ${e.message}")
                null
            }
            if (!isCurrent(ready)) return@launch
            _selected.value = if (article != null) {
                SelectedState.Ready(title, article)
            } else {
                SelectedState.Failed(title)
            }
            pruneStaleTranslations()
            val key = itemKey(title)
            if (!article?.description.isNullOrBlank() && _translations.value[key] == null) {
                maybeAutoTranslate(key)
            }
            if (!article?.infobox?.allFields().isNullOrEmpty() && isAutoTranslateEnabled()) {
                translateArticleInfobox(title)
            }
        }
    }

    fun loadSelectedSection(index: Int) {
        val selected = _selected.value as? SelectedState.Ready ?: return
        when (_selectedSections.value[index]) {
            is ArticleSectionState.Loading, is ArticleSectionState.Loaded -> return
            else -> Unit
        }
        _selectedSections.update { it + (index to ArticleSectionState.Loading) }
        selectedSectionJobs[index]?.cancel()
        selectedSectionJobs[index] = viewModelScope.launch {
            val content = try {
                FandomRepository.loadSection(
                    appContext,
                    selected.article.wikiId,
                    selected.article.pageTitle,
                    index
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Wiki makale bölümü yüklenemedi (${selected.title}/$index): ${e.message}")
                null
            }
            if (!isCurrentArticleItem(selected.title, selected.article.pageTitle)) return@launch
            val next: ArticleSectionState = if (content != null) ArticleSectionState.Loaded(content) else ArticleSectionState.Failed
            _selectedSections.update { it + (index to next) }
            pruneStaleTranslations()
            if (content != null && !content.isEmpty() && isAutoTranslateEnabled()) {
                translate(articleSectionKey(selected.title, index))
            }
        }
    }

    fun closeItem() {
        selectedJob?.cancel()
        cancelSelectedSectionJobs()
        cancelArticleItemTranslations()
        _selectedSections.value = emptyMap()
        _selected.value = SelectedState.Closed
    }

    fun retryItem(title: String) {
        openItem(title)
    }

    /**
     * Metni uygulama içi çeviriye verir. [force] = true iken eldeki çeviri yok sayılır
     * (hedef dil değiştiğinde ya da otomatik çeviri yeni açıldığında).
     */
    fun translate(key: String, force: Boolean = false) {
        if (key in _translating.value) return
        if (!force && key in _translations.value) return
        if (!force && key in _originalKeys.value) return
        val ready = _state.value as? UiState.Ready ?: return
        val articleTarget = isArticleTranslationKey(key)
        val articlePage = (_article.value as? ArticleState.Ready)?.page
        val reader = _reader.value as? ReaderState.Ready
        val readerLocation = reader?.let { FandomReaderLocation(readerBrowserController.activeLocation?.wikiId ?: ready.ref.wikiId, it.page.displayTitle) }

        // Kaynak zaten hedef dilde: "çeviri" metni değiştirmez, yalnızca kafa karıştırır
        // (Vikipedi TR maddesi + Türkçe hedef gibi). Bu kontrol yapılandırılmış bölümlerden
        // ÖNCE yapılır; aksi hâlde düz açıklama atlanırken açılır bölüm yine çevriliyordu.
        val sourceWikiId = if (FandomReaderTranslationKeys.isReaderKey(key)) {
            readerLocation?.wikiId ?: ready.ref.wikiId
        } else ready.ref.wikiId
        if (isTargetLanguageSource(sourceWikiId)) {
            translationJobs.remove(key)?.cancel()
            translationBases.remove(key)
            _translating.update { it - key }
            _originalKeys.update { it - key }
            _translations.update { it.withoutTranslation(key) }
            return
        }

        // İçindekiler satırları düz metin değil, sıralı başlık listesidir: kendi
        // yapı-korumalı akışı vardır (satır sayısı kanıtlanır, hiçbir satır kaymaz).
        if (readerLocation != null && key == FandomReaderTranslationKeys.contents(readerLocation)) {
            translateReaderContents(readerLocation, force)
            return
        }

        // YAPI KORUMASI: Bölüm içeriği düz metne indirilmez; blok blok çevrilip blok
        // anahtarlarına yazılır. Böylece liste/kart/başlık düzeni, görsel sırası ve
        // bağlantı hedefleri çeviri sonrasında da birebir aynı kalır.
        sectionStructure(key, reader, readerLocation)?.let { structure ->
            translateSectionStructured(key, structure, ready, articleTarget, articlePage, force)
            return
        }

        val text = currentTextForKey(key)
        if (text.isNullOrBlank()) return
        if (force) _translations.update { it - key }
        _translating.update { it + key }
        val generation = translationGeneration
        // Çeviri bu metne bağlanır; metin değişirse [pruneStaleTranslations] kaydı düşürür.
        translationBases[key] = text
        translationJobs[key] = viewModelScope.launch {
            try {
                // Parça parça akan çeviri: detay sayfasındaki açıklama çevirisiyle aynı davranış.
                val result = withContext(Dispatchers.IO) {
                    translationManager.translateToTarget(text) { partial ->
                        if (generation == translationGeneration && partial.isNotBlank() && isCurrent(ready) &&
                            (!articleTarget || articlePage?.let(::isCurrentArticle) == true)
                        ) {
                            _translations.update { it + (key to partial) }
                        }
                    }
                }
                if (generation != translationGeneration || !isCurrent(ready) ||
                    (articleTarget && articlePage?.let(::isCurrentArticle) != true)
                ) return@launch
                if (result.isNotBlank() && result != text) {
                    _translations.update { it + (key to result) }
                } else {
                    translationBases.remove(key)
                    _originalKeys.update { it - key }
                    _translations.update { it - key }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == translationGeneration) {
                    Log.w(TAG, "Çeviri başarısız ($key): ${e.message}")
                    translationBases.remove(key)
                    if (isCurrent(ready)) _translations.update { it - key }
                }
            } finally {
                if (generation == translationGeneration &&
                    translationJobs[key] === kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]
                ) {
                    _translating.update { it - key }
                    translationJobs.remove(key)
                }
            }
        }
    }

    /** Çeviri anahtarının şu anki kaynak metni; metin henüz yüklenmediyse null. */
    private fun currentTextForKey(key: String): String? {
        val ready = _state.value as? UiState.Ready ?: return null
        val articlePage = (_article.value as? ArticleState.Ready)?.page
        val reader = _reader.value as? ReaderState.Ready
        val readerLocation = reader?.let { FandomReaderLocation(readerBrowserController.activeLocation?.wikiId ?: ready.ref.wikiId, it.page.displayTitle) }
        return when {
            key == KEY_SUMMARY -> (_summary.value as? BlurbState.Ready)?.blurb?.description
            key == FandomCharacterWikiViewModel.KEY_DESCRIPTION -> articlePage?.description
            key.startsWith("ib:") -> {
                val index = key.removePrefix("ib:").toIntOrNull()
                index?.let { articlePage?.infobox?.allFields()?.getOrNull(it)?.value }
            }
            key.startsWith("sec:") -> {
                val index = key.removePrefix("sec:").toIntOrNull()
                (index?.let { _articleSections.value[it] } as? FandomCharacterWikiViewModel.SectionState.Loaded)
                    ?.content?.plainText()
            }
            key.startsWith("item:") -> {
                val title = key.removePrefix("item:")
                (_selected.value as? SelectedState.Ready)?.takeIf { it.title == title }?.article?.description
            }
            key.startsWith("item-sec:") -> {
                val parts = key.removePrefix("item-sec:")
                val index = parts.substringBefore(':').toIntOrNull()
                val title = parts.substringAfter(':', missingDelimiterValue = "")
                if (index == null || title.isBlank()) null
                else {
                    val current = _selected.value as? SelectedState.Ready
                    current?.takeIf { it.title == title }
                        ?.let { (_selectedSections.value[index] as? ArticleSectionState.Loaded)?.content?.plainText() }
                }
            }
            readerLocation != null && key == FandomReaderTranslationKeys.description(readerLocation) ->
                reader?.page?.description
            // İçindekiler: çevrilecek "metin" sıralı başlık listesidir. Kendi akışı
            // (`translateReaderContents`) satır sayısını jetonla kanıtlar; buradaki değer
            // yalnızca içerik parmak izi olarak kullanılır (sayfa değişince çeviri düşer).
            readerLocation != null && key == FandomReaderTranslationKeys.contents(readerLocation) ->
                reader?.page?.sections
                    ?.map { it.title.trim() }
                    ?.filter { it.isNotEmpty() }
                    ?.takeIf { it.isNotEmpty() }
                    ?.joinToString("\n")
            readerLocation != null -> reader?.sections?.entries?.firstOrNull { (index, _) ->
                key == FandomReaderTranslationKeys.section(readerLocation, index)
            }?.value?.plainText()
            else -> null
        }
    }

    /**
     * Kaynağın içerik dili çeviri hedefiyle aynı mı? (Örn. Vikipedi TR maddesi + Türkçe hedef.)
     * Aynı dildeki metni "çevirmek" içeriği değiştirmez, yalnızca kullanıcıya iki farklı metin
     * varmış izlenimi verir; bu yüzden bu kaynaklarda uygulama içi çeviri hiç başlatılmaz.
     */
    private fun isTargetLanguageSource(wikiId: String): Boolean {
        val origin = if (FandomUrls.isWikipedia(wikiId)) FandomUrls.wikipediaLang(wikiId)
        else FandomUrls.languageOf(wikiId)
        val target = KitsugiTranslatePrefs.resolvedTarget()
        if (origin.isNullOrBlank() || target.isBlank()) return false
        return origin.equals(target.substringBefore('-'), ignoreCase = true)
    }

    /**
     * Metni değişen anahtarların çevirisini (ve Orijinal/Çeviri durumunu) düşürür.
     * Kaynak/madde değişiminde eski çevirinin ekranda kalmasını yapısal olarak engeller.
     */
    private fun pruneStaleTranslations() {
        if (translationBases.isEmpty()) return
        val stale = translationBases.entries
            // null burada "içerik henüz yüklenmedi" de olabilir. Kaynağa geri dönerken
            // açıklama özeti makaleden sonra geldiği için null'u bayat saysaydık bellekteki
            // çeviriyi daha kendi metni yüklenmeden silerdik.
            .filter { (key, base) -> currentTextForKey(key)?.let { it != base } == true }
            .map { it.key }
        if (stale.isEmpty()) return
        stale.forEach { translationBases.remove(it) }
        val staleSet = stale.toSet()
        staleSet.forEach { key -> translationJobs.remove(key)?.cancel() }
        _translating.update { it - staleSet }
        _originalKeys.update { it - staleSet }
        _translations.update { it - staleSet }
    }

    fun translateInfobox() {
        val page = (_article.value as? ArticleState.Ready)?.page ?: return
        val boxKey = FandomCharacterWikiViewModel.KEY_INFOBOX
        if (boxKey in _translating.value || boxKey in _translations.value || boxKey in _originalKeys.value) return
        val fields = page.infobox?.allFields().orEmpty()
        val targets = fields.withIndex()
            .filter { (_, field) -> field.value.isNotBlank() && FandomInfoboxLabels.valueResId(field.value) == null }
            .take(60)
        if (targets.isEmpty()) {
            _translations.update { it + (boxKey to "1") }
            return
        }
        val ready = _state.value as? UiState.Ready ?: return
        _translating.update { it + boxKey }
        val generation = translationGeneration
        translationJobs[boxKey] = viewModelScope.launch {
            try {
                val translated = withContext(Dispatchers.IO) {
                    val lines = targets.map { it.value.value.replace(Regex("\\s*\\n\\s*"), " · ").trim() }
                    // YAPI KORUMASI: Satır sayısı kanıtlanır (⟪§⟫ ayırıcı jetonu). Google
                    // satırları birleştirir/bölerse tek istek terk edilir ve alanlar tek tek
                    // çevrilir; etiket-değer eşleşmesi hiçbir koşulda kaymaz.
                    translateRowsPreservingStructure(lines) { line ->
                        translationManager.translateToTarget(line)
                    }
                }
                if (generation != translationGeneration || !isCurrent(ready) || !isCurrentArticle(page)) return@launch
                val additions = HashMap<String, String>()
                targets.forEachIndexed { position, (fieldIndex, field) ->
                    translated.getOrNull(position)?.takeIf { it.isNotBlank() && it != field.value }?.let {
                        additions[FandomCharacterWikiViewModel.infoboxFieldKey(fieldIndex)] = it
                    }
                }
                additions[boxKey] = "1"
                _translations.update { it + additions }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == translationGeneration) {
                    Log.w(TAG, "Bilgi kutusu çevirisi başarısız: ${e.message}")
                }
            } finally {
                if (generation == translationGeneration &&
                    translationJobs[boxKey] === kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]
                ) {
                    _translating.update { it - boxKey }
                    translationJobs.remove(boxKey)
                }
            }
        }
    }

    /**
     * Okuyucu İçindekiler satırlarını TEK istekte çevirir.
     *
     * Satırlar `⟪§⟫` ayırıcı jetonuyla gönderilir ve dönen satır sayısı kanıtlanır; sayı
     * tutmazsa her başlık kendi başına çevrilir. Böylece hiçbir başlık yanlış bölümün
     * karşısına düşmez. Sonuç [FandomReaderTranslationKeys.contents] anahtarının çocukları
     * olarak saklanır; düz metin değildir, bu yüzden panoya/başka yüzeye sızmaz.
     */
    private fun translateReaderContents(location: FandomReaderLocation, force: Boolean = false) {
        val ready = _state.value as? UiState.Ready ?: return
        val reader = _reader.value as? ReaderState.Ready ?: return
        val key = FandomReaderTranslationKeys.contents(location)
        if (!force && key in _translating.value) return
        if (!force && key in _translations.value) return
        if (!force && key in _originalKeys.value) return
        if (isTargetLanguageSource(ready.ref.wikiId)) return
        val titles = reader.page.sections.map { it.title.trim() }.filter { it.isNotEmpty() }
        if (titles.isEmpty()) return
        // İçerik parmak izi: sayfa değişir ya da başlık listesi güncellenirse
        // [pruneStaleTranslations] bu çeviriyi düşürür (başka sayfanın satırları kalmaz).
        translationBases[key] = titles.joinToString("\n")
        if (force) _translations.update { it.withoutTranslation(key) }
        _translating.update { it + key }
        val generation = translationGeneration
        translationJobs[key] = viewModelScope.launch {
            try {
                val translated = withContext(Dispatchers.IO) {
                    translateRowsPreservingStructure(titles) { row -> translationManager.translateToTarget(row) }
                }
                if (generation != translationGeneration || !isCurrent(ready)) return@launch
                val active = _reader.value as? ReaderState.Ready ?: return@launch
                if (active.page.displayTitle != reader.page.displayTitle) return@launch
                val additions = HashMap<String, String>()
                translated.forEachIndexed { index, value ->
                    if (value.isNotBlank() && value != titles[index]) {
                        additions[WikiStructuredTranslationKeys.block(key, index)] = value
                    }
                }
                if (additions.isEmpty()) {
                    _translations.update { it.withoutTranslation(key) }
                } else {
                    additions[key] = WikiStructuredTranslationKeys.COMPLETE
                    _translations.update { it + additions }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "İçindekiler çevirisi başarısız ($key): ${e.message}")
            } finally {
                if (generation == translationGeneration &&
                    translationJobs[key] === kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]
                ) {
                    _translating.update { it - key }
                    translationJobs.remove(key)
                }
            }
        }
    }

    /**
     * Okuyucu sayfasının TAMAMI için Orijinal/Çeviri geçişi.
     *
     * "Orijinal"e dönmek çeviriyi SİLMEZ (bellekte kalır): "Çeviri" seçildiğinde ağ isteği
     * olmadan anında geri gelir. Orijinal modda yüklenen bölümler çevrilmemiş olacağı için
     * çeviriye dönerken yalnızca çevirisi olmayan yüzeyler çeviriye verilir (otomatik
     * çeviri açıksa; düğme zaten yalnızca ekranda çeviri varken görünür).
     */
    fun setReaderOriginal(showOriginal: Boolean) {
        val ready = _state.value as? UiState.Ready ?: return
        val reader = _reader.value as? ReaderState.Ready ?: return
        val location = FandomReaderLocation(ready.ref.wikiId, reader.page.displayTitle)
        if (location.title.isBlank()) return
        val keys = readerTranslationKeys(location, reader)
        if (showOriginal) {
            _originalKeys.update { it + keys }
            return
        }
        _originalKeys.update { it - keys }
        viewModelScope.launch {
            if (!isAutoTranslateEnabled()) return@launch
            keys.forEach { key ->
                if (!_translations.value.hasWikiTranslation(key)) translate(key)
            }
        }
    }

    /** Okuyucu sayfasındaki tüm çevrilebilir yüzeylerin anahtarları. */
    private fun readerTranslationKeys(
        location: FandomReaderLocation,
        reader: ReaderState.Ready
    ): Set<String> = buildSet {
        add(FandomReaderTranslationKeys.description(location))
        add(FandomReaderTranslationKeys.contents(location))
        reader.sections.keys.forEach { index ->
            add(FandomReaderTranslationKeys.section(location, index))
        }
        reader.page.sections.forEach { section ->
            add(FandomReaderTranslationKeys.section(location, section.index))
        }
    }

    fun showOriginal(key: String) {
        if (key == FandomCharacterWikiViewModel.KEY_INFOBOX) {
            _translations.update { map -> map.filterKeys { it != key && !it.startsWith("ib:") } }
        } else {
            // Bölüm çevirisinde blok çocukları da bırakılır; yarım çeviri ekranda kalmaz.
            _translations.update { it.withoutTranslation(key) }
        }
    }

    /**
     * Anahtar bir bölüm içeriğine karşılık geliyorsa o içeriği döner; aksi hâlde `null`
     * (özet/açıklama/bilgi kutusu gibi düz metin akışları kullanılır).
     */
    private fun sectionStructure(
        key: String,
        reader: ReaderState.Ready?,
        readerLocation: FandomReaderLocation?
    ): FandomSectionContent? {
        if (key.startsWith("item-sec:")) {
            val parts = key.removePrefix("item-sec:")
            val index = parts.substringBefore(':').toIntOrNull() ?: return null
            val title = parts.substringAfter(':', missingDelimiterValue = "")
            val current = _selected.value as? SelectedState.Ready ?: return null
            if (title.isBlank() || current.title != title) return null
            return (_selectedSections.value[index] as? ArticleSectionState.Loaded)?.content
        }
        if (key.startsWith("sec:")) {
            val index = key.removePrefix("sec:").toIntOrNull() ?: return null
            return (_articleSections.value[index] as? FandomCharacterWikiViewModel.SectionState.Loaded)?.content
        }
        if (reader == null || readerLocation == null) return null
        return reader.sections.entries
            .firstOrNull { (index, _) -> key == FandomReaderTranslationKeys.section(readerLocation, index) }
            ?.value
    }

    /**
     * Bölümü blok blok çevirir. Tamamlanan her blok anında arayüze akar; sonuçta bölüm
     * anahtarına "tamam" işareti yazılır. Hiçbir blok çevrilemezse (örn. metin zaten hedef
     * dilde) çeviri tamamen geri alınır ve bölüm orijinal hâliyle kalır.
     */
    private fun translateSectionStructured(
        key: String,
        content: FandomSectionContent,
        ready: UiState.Ready,
        articleTarget: Boolean,
        articlePage: FandomCharacterPage?,
        force: Boolean
    ) {
        if (force) _translations.update { it.withoutTranslation(key) }
        _translating.update { it + key }
        val generation = translationGeneration
        translationJobs[key] = viewModelScope.launch {
            fun isAlive(): Boolean = generation == translationGeneration && isCurrent(ready) &&
                (!articleTarget || articlePage?.let(::isCurrentArticle) == true)
            try {
                val entries = withContext(Dispatchers.IO) {
                    translateSectionBlocks(
                        baseKey = key,
                        content = content,
                        translate = { chunk -> translationManager.translateToTarget(chunk) },
                        onBlock = { childKey, value ->
                            // Her blok anında yansır; bölüm anahtarına işaret de yazılır ki
                            // "çevrildi/orijinal" düğmesi ilk blokla birlikte doğru duruma geçsin.
                            if (isAlive()) {
                                _translations.update {
                                    // Tek harita kopyası: akış sırasında her blok için iki
                                    // tam kopya yerine bir kopya (büyük önbelleklerde GC baskısını düşürür).
                                    it + (mapOf(childKey to value, key to WikiStructuredTranslationKeys.COMPLETE))
                                }
                            }
                        }
                    )
                }
                if (!isAlive()) return@launch
                if (entries.isEmpty()) {
                    _translations.update { it.withoutTranslation(key) }
                } else {
                    _translations.update { it + (entries + (key to WikiStructuredTranslationKeys.COMPLETE)) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Bölüm çevirisi başarısız ($key): ${e.message}")
                if (generation == translationGeneration) _translations.update { it.withoutTranslation(key) }
            } finally {
                if (generation == translationGeneration &&
                    translationJobs[key] === kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]
                ) {
                    _translating.update { it - key }
                    translationJobs.remove(key)
                }
            }
        }
    }

    /**
     * Şu an bellekte olan tüm wiki metinlerini çevirir (seri özeti + açık kart açıklaması + madde).
     * Otomatik çeviri ayarı açıldığında ya da hedef dil değiştiğinde çağrılır.
     */
    private fun translateArticleInfobox(title: String, force: Boolean = false) {
        val infoKey = articleInfoboxKey(title)
        if (force) {
            translationJobs.remove(infoKey)?.cancel()
            _translating.update { it - infoKey }
            _translations.update { map -> map.filterKeys { it != infoKey && !it.startsWith("item-if:") } }
        }
        if (infoKey in _translating.value) return
        if (!force && infoKey in _translations.value) return
        val article = (_selected.value as? SelectedState.Ready)
            ?.takeIf { it.title == title }?.article ?: return
        val targets = article.infobox?.allFields().orEmpty().withIndex()
            .filter { (_, field) ->
                field.value.isNotBlank() && FandomInfoboxLabels.valueResId(field.value) == null
            }
            .take(40)
        if (targets.isEmpty()) {
            _translations.update { it + (infoKey to "1") }
            return
        }
        _translating.update { it + infoKey }
        val generation = translationGeneration
        translationJobs[infoKey] = viewModelScope.launch {
            try {
                val translatedValues = withContext(Dispatchers.IO) {
                    val sourceLines = targets.map { (_, field) -> field.value.replace(Regex("\\s*\\n\\s*"), " · ").trim() }
                    // YAPI KORUMASI: Satır sayısı kanıtlanır (⟪§⟫ ayırıcı jetonu); kanıtlanamazsa
                    // alanlar tek tek çevrilir ve sıra hiçbir koşulda kaymaz.
                    translateRowsPreservingStructure(sourceLines) { row ->
                        translationManager.translateToTarget(row)
                    }
                }
                if (generation != translationGeneration || !isCurrentArticleItem(title, article.pageTitle)) return@launch
                val additions = HashMap<String, String>()
                targets.forEachIndexed { position, (fieldIndex, field) ->
                    val translated = translatedValues.getOrNull(position).orEmpty()
                    if (translated.isNotBlank() && translated != field.value) {
                        additions[articleInfoboxFieldKey(title, fieldIndex)] = translated
                    }
                }
                additions[infoKey] = "1"
                _translations.update { it + additions }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == translationGeneration) {
                    Log.w(TAG, "Wiki bilgi kutusu çevirisi başarısız ($title): ${e.message}")
                }
            } finally {
                if (generation == translationGeneration &&
                    translationJobs[infoKey] === kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]
                ) {
                    _translating.update { it - infoKey }
                    translationJobs.remove(infoKey)
                }
            }
        }
    }

    private fun cancelSelectedSectionJobs() {
        selectedSectionJobs.values.forEach { it.cancel() }
        selectedSectionJobs.clear()
    }

    private fun cancelArticleItemTranslations() {
        val keys = translationJobs.keys.filter { it.startsWith("item") }
        keys.forEach { key ->
            translationJobs.remove(key)?.cancel()
            _translating.update { it - key }
        }
        _translations.update { map -> map.filterKeys { !it.startsWith("item") } }
    }

    private fun isCurrentArticleItem(title: String, pageTitle: String): Boolean {
        val current = _selected.value as? SelectedState.Ready ?: return false
        return current.title == title && current.article.pageTitle == pageTitle
    }

    private fun retranslateLoadedTexts(force: Boolean) {
        if (force) {
            cancelTranslations()
            _translations.value = emptyMap()
            _originalKeys.value = emptySet()
        }
        if (!(_summary.value as? BlurbState.Ready)?.blurb?.description.isNullOrBlank()) {
            translate(KEY_SUMMARY, force = force)
        }
        val selected = _selected.value as? SelectedState.Ready
        if (selected != null) {
            if (!selected.article.description.isNullOrBlank()) {
                translate(itemKey(selected.title), force = force)
            }
            if (!selected.article.infobox?.allFields().isNullOrEmpty()) {
                translateArticleInfobox(selected.title, force = force)
            }
            _selectedSections.value.forEach { (index, state) ->
                if (state is ArticleSectionState.Loaded) {
                    translate(articleSectionKey(selected.title, index), force = force)
                }
            }
        }
        val articlePage = (_article.value as? ArticleState.Ready)?.page
        if (articlePage != null) {
            if (!articlePage.description.isNullOrBlank()) {
                translate(FandomCharacterWikiViewModel.KEY_DESCRIPTION, force = force)
            }
            if (!articlePage.infobox?.allFields().isNullOrEmpty()) {
                translateInfobox()
            }
            _articleSections.value.keys.sorted().forEach { index ->
                translate(FandomCharacterWikiViewModel.sectionKey(index), force = force)
            }
        }
        val reader = _reader.value as? ReaderState.Ready
        if (reader != null) {
            val location = FandomReaderLocation(readerBrowserController.activeLocation?.wikiId.orEmpty(), reader.page.displayTitle)
            if (location.wikiId.isNotBlank() && !reader.page.description.isNullOrBlank()) {
                translate(FandomReaderTranslationKeys.description(location), force = force)
            }
            if (location.wikiId.isNotBlank()) {
                reader.sections.keys.sorted().forEach { index ->
                    translate(FandomReaderTranslationKeys.section(location, index), force = force)
                }
                translate(FandomReaderTranslationKeys.contents(location), force = force)
            }
        }
    }

    fun reportWrongMatch() {
        val ready = _state.value as? UiState.Ready ?: return
        val query = lastQuery ?: return
        viewModelScope.launch {
            try {
                FandomRepository.rejectMediaWiki(appContext, query, ready.ref)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Wiki engellenemedi: ${e.message}")
            }
            rebind()
            // `rebind()` senkron tamamlanır; kartı ondan sonra koymak bağlama sıfırlamasına
            // takılmadan (aynı anahtar) kartın yeni durumda da görünmesini sağlar.
            lastRejectedBindingKey = boundKey
            _lastRejected.value = ready.ref
        }
    }

    /** Geri alma kartındaki "Geri al": reddi kaldırır ve wiki'yi tekrar çözümletir. */
    fun undoLastReject() {
        val rejected = _lastRejected.value ?: return
        val query = lastQuery ?: return
        viewModelScope.launch {
            try {
                FandomRepository.undoRejectMediaWiki(appContext, query, rejected)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Ret geri alınamadı: ${e.message}")
            }
            lastRejectedBindingKey = null
            _lastRejected.value = null
            rebind()
        }
    }

    /** Geri alma kartını kapatır; ret kalıcıdır, Ayarlar'dan yine de geri getirilebilir. */
    fun dismissLastRejected() {
        lastRejectedBindingKey = null
        _lastRejected.value = null
    }

    // ── Uygulama içi wiki okuyucu ────────────────────────────────────────────

    /**
     * Üst başlıktaki "Fandom sayfasını" / "Vikipedi'de oku" düğmesi.
     *
     * Vikipedi'de ansiklopedinin ANA SAYFASI ("Anasayfa") hiçbir işe yaramaz: kullanıcı yapımın
     * maddesini beklerken günün seçkin maddelerini görüyordu. Bu yüzden Vikipedi kaynağında
     * önce eşleşen madde, madde yoksa ana sayfa açılır. Fandom'da davranış aynıdır (wiki ana
     * sayfası = yapımın hub'ı).
     */
    fun openReaderMainPage() {
        val ready = _state.value as? UiState.Ready ?: return
        if (FandomUrls.isWikipedia(ready.ref.wikiId)) {
            val articleTitle = (_article.value as? ArticleState.Ready)?.page?.pageTitle
                ?.takeIf { it.isNotBlank() }
                ?: ready.ref.pageTitle?.takeIf { it.isNotBlank() }
            if (articleTitle != null) {
                openReader(articleTitle, pushToBackStack = false)
                return
            }
        }
        viewModelScope.launch {
            val main = FandomWikiResolver.siteInfoCached(ready.ref.wikiId)?.mainPage
                ?: ready.ref.siteName
                ?: ready.ref.wikiId
            openReader(main, pushToBackStack = false)
        }
    }

    /** Bir sayfayı uygulama İÇİNDE okuyucu olarak açar; bağlantılarda history'ye eklenir. */
    fun openReader(title: String, pushToBackStack: Boolean = true) {
        val ready = _state.value as? UiState.Ready ?: return
        val clean = title.trim()
        if (clean.isEmpty()) return
        val location = FandomReaderLocation(ready.ref.wikiId, clean)
        readerBrowserController.navigate(location, pushHistory = pushToBackStack)
        _readerBrowser.value = readerBrowserController.snapshot
        loadReaderLocation(location)
    }

    // Original pages are loaded by WebView. Do not also parse/translate the same page
    // in the background, or let an API error prevent a working website from opening.
    fun setReaderWebMode(enabled: Boolean) {
        if (readerWebMode == enabled) return
        readerWebMode = enabled
        readerBrowserController.activeLocation?.let { loadReaderLocation(it) }
    }

    fun updateReaderWebLocation(tabId: Long, location: FandomReaderLocation) {
        if (!readerWebMode || FandomUrls.normalize(location.wikiId) == null) return
        if (readerBrowserController.updateWebLocation(tabId, location)) {
            _readerBrowser.value = readerBrowserController.snapshot
        }
    }

    private fun loadReaderLocation(location: FandomReaderLocation, forceRefresh: Boolean = false) {
        val ready = _state.value as? UiState.Ready ?: return
        readerJob?.cancel()
        readerSectionJobs.values.forEach { it.cancel() }
        readerSectionJobs.clear()
        readerRelatedJob?.cancel()
        _reader.value = ReaderState.Loading
        if (readerWebMode) return
        readerJob = viewModelScope.launch {
            val page = try {
                FandomRepository.loadFullPageMeta(appContext, location.wikiId, location.title, forceRefresh)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Okuyucu sayfa yüklenemedi (${location.title}): ${e.message}")
                null
            }
            if (!isCurrent(ready) || readerBrowserController.activeLocation != location) return@launch
            if (page == null) {
                _reader.value = ReaderState.Failed(location.title)
                return@launch
            }
            val resolvedLocation = location.copy(title = page.displayTitle)
            readerBrowserController.normalizeCurrentLocation(resolvedLocation)
            _readerBrowser.value = readerBrowserController.snapshot
            _reader.value = ReaderState.Ready(
                page = page,
                sections = emptyMap(),
                related = emptyList()
            )
            pruneStaleTranslations()
            launchRelatedLoadForReader(location.wikiId, page)
            // Giriş bölümü (section 0) her zaman yüklenir: makale özeti veya ana sayfanın
            // tüm modülleri (hero, öne çıkan makale, kartlar, içerik kutuları) buradan gelir.
            loadReaderSection(0)
            if (isAutoTranslateEnabled()) {
                if (!page.description.isNullOrBlank()) {
                    translate(FandomReaderTranslationKeys.description(resolvedLocation))
                }
                // İçindekiler satırları da seçili dile gelir; sayfa "bütün olarak" çevrilir.
                translateReaderContents(resolvedLocation)
            }
        }
    }

    private fun launchRelatedLoadForReader(wikiId: String, page: FandomFullPage) {
        readerRelatedJob?.cancel()
        readerRelatedJob = viewModelScope.launch {
            val related = try {
                if (FandomUrls.isWikipedia(wikiId)) {
                    // Okuyucudaki şerit de aynı kuralı kullanır: Vikipedi'de bilgi kutusu
                    // bağlantıları (ülke/bölge) ve pageid sıralaması kartlara yansımaz.
                    FandomRepository.loadWikipediaRelatedPages(
                        context = appContext,
                        wikiId = wikiId,
                        pageTitle = page.displayTitle,
                        selfTitles = listOf(page.displayTitle),
                        workTitles = lastQuery?.fallbackTitles.orEmpty()
                    )
                } else {
                    val candidates = LinkedHashSet<String>()
                    for (idx in listOf(0, 1)) {
                        val section = FandomRepository.loadSection(appContext, wikiId, page.displayTitle, idx)
                        if (section != null) candidates += section.links
                    }
                    val titles = candidates.take(30)
                    if (titles.isEmpty()) emptyList()
                    else FandomRepository.loadRelatedPages(appContext, wikiId, titles)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Okuyucu ilgili sayfalar yüklenemedi (${page.displayTitle}): ${e.message}")
                emptyList()
            }
            val cur = _reader.value as? ReaderState.Ready ?: return@launch
            if (cur.page.displayTitle != page.displayTitle || readerBrowserController.activeLocation?.wikiId != wikiId) return@launch
            _reader.value = cur.copy(related = related.filter { !it.title.equals(page.displayTitle, ignoreCase = true) }.take(20))
        }
    }

    /** Okuyucu: bir bölümü yükler; açılan birden fazla ToC bölümü paralel kalabilir. */
    fun loadReaderSection(index: Int) {
        val ready = _state.value as? UiState.Ready ?: return
        val cur = _reader.value as? ReaderState.Ready ?: return
        if (cur.sections.containsKey(index) || index in cur.loadingSections) return
        val location = readerBrowserController.activeLocation
            ?: FandomReaderLocation(ready.ref.wikiId, cur.page.displayTitle)
        _reader.value = cur.copy(
            loadingSections = cur.loadingSections + index,
            failedSections = cur.failedSections - index
        )
        val job = viewModelScope.launch {
            try {
                val content = try {
                    FandomRepository.loadSection(appContext, location.wikiId, cur.page.displayTitle, index)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Okuyucu bölüm yüklenemedi (${cur.page.displayTitle}#$index): ${e.message}")
                    null
                }
                val now = _reader.value as? ReaderState.Ready ?: return@launch
                if (now.page.displayTitle != cur.page.displayTitle ||
                    readerBrowserController.activeLocation?.wikiId != location.wikiId
                ) return@launch
                if (content == null) {
                    _reader.value = now.copy(
                        loadingSections = now.loadingSections - index,
                        failedSections = now.failedSections + index
                    )
                    return@launch
                }
                _reader.value = now.copy(
                    sections = now.sections + (index to content),
                    loadingSections = now.loadingSections - index,
                    failedSections = now.failedSections - index
                )
                pruneStaleTranslations()
                if (!content.isEmpty() && isAutoTranslateEnabled()) {
                    val currentLocation = location.copy(title = cur.page.displayTitle)
                    translate(FandomReaderTranslationKeys.section(currentLocation, index))
                }
            } finally {
                if (readerSectionJobs[index] === kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]) {
                    readerSectionJobs.remove(index)
                }
            }
        }
        readerSectionJobs[index] = job
    }

    fun retryReaderSection(index: Int) = loadReaderSection(index)

    /**
     * Sayfa içi bağlantı / ilişkili sayfa kartı: Chrome/Brave gibi aktif sekmenin hemen
     * sağında YENİ sekmede açılır (kaynak sayfa yerinde kalır). Aynı sayfa zaten açık bir
     * sekmedeyse ona geçilir; sekme sınırı doluysa bağlantı ölü doğmasın diye aktif
     * sekmede geçmişe eklenerek açılır.
     */
    fun openReaderLink(title: String) {
        val ready = _state.value as? UiState.Ready ?: return
        val clean = title.trim()
        if (clean.isEmpty()) return
        openReaderLocationInNewTab(FandomReaderLocation(
            readerBrowserController.activeLocation?.wikiId ?: ready.ref.wikiId, clean))
    }

    private fun openReaderLocationInNewTab(location: FandomReaderLocation) {
        val before = readerBrowserController.snapshot
        val target = readerBrowserController.openInNewTab(location)
        if (target == null) {
            // Sekme sınırı (12) dolu: aynı sekmede geçmişli gezinmeye düş.
            readerBrowserController.navigate(location, pushHistory = true)
            _readerBrowser.value = readerBrowserController.snapshot
            loadReaderLocation(location)
            return
        }
        _readerBrowser.value = readerBrowserController.snapshot
        // Bağlantı zaten aktif sekmedeki sayfanın kendisiyse yeniden yükleme yapma.
        if (readerBrowserController.snapshot.activeTabId == before.activeTabId &&
            target == before.activeTab?.location
        ) return
        loadReaderLocation(target)
    }

    /** Retries a failed page or TOC without adding another browser history entry. */
    fun retryReaderPage() {
        val location = readerBrowserController.activeLocation ?: return
        loadReaderLocation(location, forceRefresh = true)
    }

    fun readerBack(): Boolean {
        val previous = readerBrowserController.back()
        if (previous == null) {
            closeReader()
            return false
        }
        _readerBrowser.value = readerBrowserController.snapshot
        loadReaderLocation(previous)
        return true
    }

    fun readerForward(): Boolean {
        val next = readerBrowserController.forward() ?: return false
        _readerBrowser.value = readerBrowserController.snapshot
        loadReaderLocation(next)
        return true
    }

    fun newReaderTab(): Boolean {
        val location = readerBrowserController.openNewTab() ?: return false
        _readerBrowser.value = readerBrowserController.snapshot
        loadReaderLocation(location)
        return true
    }

    fun switchReaderTab(tabId: Long) {
        val location = readerBrowserController.switchTab(tabId) ?: return
        _readerBrowser.value = readerBrowserController.snapshot
        loadReaderLocation(location)
    }

    fun closeReaderTab(tabId: Long) {
        val before = readerBrowserController.snapshot
        if (before.tabs.size == 1 && before.activeTabId == tabId) {
            closeReader()
            return
        }
        val wasActive = before.activeTabId == tabId
        val nextLocation = readerBrowserController.closeTab(tabId)
        _readerBrowser.value = readerBrowserController.snapshot
        if (wasActive && nextLocation != null) loadReaderLocation(nextLocation)
    }

    /** Sekmeye uzun basma: diğer tüm sekmeleri kapat, bunu tut. */
    fun closeOtherReaderTabs(tabId: Long) {
        val before = readerBrowserController.snapshot
        if (before.tabs.size <= 1) return
        val location = readerBrowserController.closeOtherTabs(tabId) ?: return
        _readerBrowser.value = readerBrowserController.snapshot
        if (before.activeTabId != tabId) loadReaderLocation(location)
    }

    fun closeReader() {
        readerJob?.cancel()
        readerSectionJobs.values.forEach { it.cancel() }
        readerSectionJobs.clear()
        readerRelatedJob?.cancel()
        readerWebMode = true
        readerBrowserController.reset()
        _readerBrowser.value = FandomReaderBrowserState.Empty
        _reader.value = ReaderState.Closed
    }

    private fun isCurrent(ready: UiState.Ready): Boolean {
        val current = _state.value as? UiState.Ready ?: return false
        return current.generation == ready.generation && current.mediaKey == ready.mediaKey &&
            current.resolutionKey == ready.resolutionKey && current.ref.wikiId == ready.ref.wikiId &&
            current.ref.source == ready.ref.source && current.ref.confidence == ready.ref.confidence
    }

    private fun isCurrentArticle(page: FandomCharacterPage): Boolean {
        val current = (_article.value as? ArticleState.Ready)?.page ?: return false
        return current.wikiId == page.wikiId && current.pageTitle == page.pageTitle
    }

    private fun isArticleTranslationKey(key: String): Boolean =
        key == FandomCharacterWikiViewModel.KEY_DESCRIPTION ||
            key == FandomCharacterWikiViewModel.KEY_INFOBOX ||
            key.startsWith("ib:") || key.startsWith("sec:")

    private suspend fun isAutoTranslateEnabled(): Boolean = try {
        SettingsDataStore(appContext).settingsFlow.first().autoTranslateEnabled
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    private suspend fun maybeAutoTranslate(key: String) {
        // Otomatik çeviri yalnızca kullanıcı ayarı açıksa; karakter sekmesiyle aynı davranış.
        if (isAutoTranslateEnabled()) translate(key)
    }

    private fun cancelTranslations() {
        translationGeneration += 1
        translationJobs.values.forEach { it.cancel() }
        translationJobs.clear()
        _translating.value = emptySet()
        // Yeni wiki / otomatik çeviri kapandı: eski orijinal-geçiş durumu geçerli değil.
        _originalKeys.value = emptySet()
    }

    override fun onCleared() {
        super.onCleared()
        cancelTranslations()
    }
}
