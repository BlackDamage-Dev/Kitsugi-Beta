package com.kitsugi.animelist.ui.screens.detail.wiki

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kitsugi.animelist.data.fandom.FandomCharacterPage
import com.kitsugi.animelist.data.fandom.FandomFullPage
import com.kitsugi.animelist.data.fandom.FandomRelatedPage
import java.util.LinkedHashSet
import com.kitsugi.animelist.data.fandom.FandomCharacterQueries
import com.kitsugi.animelist.data.fandom.FandomCharacterQuery
import com.kitsugi.animelist.data.fandom.FandomRepository
import com.kitsugi.animelist.data.fandom.FandomUrls
import com.kitsugi.animelist.data.fandom.FandomSectionContent
import com.kitsugi.animelist.data.fandom.FandomWikiResolver
import com.kitsugi.animelist.data.fandom.FandomWikiPrefs
import com.kitsugi.animelist.data.local.TranslationManager
import com.kitsugi.animelist.data.remote.KitsugiCharacterDetail
import com.kitsugi.animelist.data.remote.KitsugiStaffDetail
import com.kitsugi.animelist.data.settings.SettingsDataStore
import com.kitsugi.animelist.utils.KitsugiTranslatePrefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Karakter detayındaki "Wiki" sekmesinin durumu.
 *
 * Sayfa açılınca yalnızca "bu karakterin wiki sayfası var mı?" çözülür (kimlik önce,
 * isim son çare). Sekme ancak [UiState.Ready] olduğunda görünür; bölüm metinleri
 * kullanıcı açtıkça yüklenir.
 */
class FandomCharacterWikiViewModel(application: Application) : AndroidViewModel(application) {

    sealed interface UiState {
        data object Idle : UiState
        data object Loading : UiState
        data class Ready(val page: FandomCharacterPage) : UiState
        data object Unavailable : UiState
    }

    sealed interface SectionState {
        data object Loading : SectionState
        data class Loaded(val content: FandomSectionContent) : SectionState
        data object Failed : SectionState
    }

    /** Uygulama içi wiki okuyucu durumu (medya sekmesiyle aynı sözleşme). */
    sealed interface ReaderState {
        data object Closed : ReaderState
        data object Loading : ReaderState
        data class Ready(
            val wikiId: String,
            val page: FandomFullPage,
            val sections: Map<Int, FandomSectionContent>,
            val related: List<FandomRelatedPage>,
            val loadingSections: Set<Int> = emptySet(),
            val failedSections: Set<Int> = emptySet()
        ) : ReaderState
        data class Failed(val title: String) : ReaderState
    }


    companion object {
        private const val TAG = "FandomWikiVM"
        const val KEY_DESCRIPTION = "desc"
        /** Bilgi kutusu toplu çeviri durumu; alan bazlı sonuçlar [infoboxFieldKey] ile saklanır. */
        const val KEY_INFOBOX = "infobox"
        const val INFOBOX_FIELD_PREFIX = "ib:"
        private const val MAX_INFOBOX_FIELDS = 60
        fun sectionKey(index: Int): String = "sec:$index"
        /** Düzleştirilmiş alan sırasına (grup → alan) göre anahtar. */
        fun infoboxFieldKey(index: Int): String = "$INFOBOX_FIELD_PREFIX$index"
    }

    private val appContext = application.applicationContext
    private val translationManager by lazy { TranslationManager(appContext) }
    private val settingsDataStore by lazy { SettingsDataStore(appContext) }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _sections = MutableStateFlow<Map<Int, SectionState>>(emptyMap())
    val sections: StateFlow<Map<Int, SectionState>> = _sections.asStateFlow()

    private val _reader = MutableStateFlow<ReaderState>(ReaderState.Closed)
    val reader: StateFlow<ReaderState> = _reader.asStateFlow()

    private val _relatedPages = MutableStateFlow<List<FandomRelatedPage>>(emptyList())
    val relatedPages: StateFlow<List<FandomRelatedPage>> = _relatedPages.asStateFlow()
    private var relatedJob: Job? = null

    // Son reddedilen sayfa: kullanıcı "Hayır, yanlış"a bastıktan sonra sekmenin en üstünde
    // "Geri al" kartı olarak gösterilir. Farklı bir karaktere geçildiğinde düşürülür.
    private var lastRejectedBindingKey: String? = null
    private val _lastRejected = MutableStateFlow<FandomCharacterPage?>(null)
    val lastRejected: StateFlow<FandomCharacterPage?> = _lastRejected.asStateFlow()

    private var readerJob: Job? = null
    private val readerSectionJobs = HashMap<Int, Job>()
    private var readerRelatedJob: Job? = null
    private var readerWebMode = true
    private val readerBrowserController = FandomReaderBrowserController()
    private val _readerBrowser = MutableStateFlow(FandomReaderBrowserState.Empty)
    val readerBrowser: StateFlow<FandomReaderBrowserState> = _readerBrowser.asStateFlow()


    /** Çevrilmiş metinler: [KEY_DESCRIPTION] veya [sectionKey]. */
    private val _translations = MutableStateFlow<Map<String, String>>(emptyMap())
    val translations: StateFlow<Map<String, String>> = _translations.asStateFlow()

    private val _translating = MutableStateFlow<Set<String>>(emptySet())
    val translating: StateFlow<Set<String>> = _translating.asStateFlow()

    /**
     * Orijinal metne dönülmüş anahtarlar ([KEY_DESCRIPTION], [sectionKey], [KEY_INFOBOX]).
     * Çeviri bellekte kalır; geçiş düğmesiyle tekrar gösterilebilir. Bu anahtarlar için
     * otomatik çeviri yeniden çalışıp üstüne yazmaz.
     */
    private val _originalKeys = MutableStateFlow<Set<String>>(emptySet())
    val originalKeys: StateFlow<Set<String>> = _originalKeys.asStateFlow()

    /**
     * Ayarlardaki çeviri uygulaması tercihi ("GOOGLE", "DEEPL", "TRANSLATE_YOU", "DEFAULT").
     * Wiki çeviri düğmeleri de detay sayfasındaki açıklama düğmesiyle aynı davrandığı için
     * (dokununca harici çeviri uygulaması açılır) bu tercih UI'ya akıtılır.
     */
    private val _preferredTranslator = MutableStateFlow("DEFAULT")
    val preferredTranslator: StateFlow<String> = _preferredTranslator.asStateFlow()

    val prefs: StateFlow<FandomWikiPrefs.State> = FandomWikiPrefs.stateFlow

    private var boundKey: String? = null
    /** Son bağlanan sorgu üreticisi; tercih değişince aynı girdiyle yeniden çözmek için. */
    private var lastBinding: (() -> Pair<FandomCharacterQuery?, String>)? = null
    private var currentQuery: FandomCharacterQuery? = null
    private var resolveJob: Job? = null
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
        // Detay sayfalarındaki açıklama çevirisiyle aynı tepkisellik: sayfa açıkken
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

    /** Karakter detayı yüklendiğinde (ve yenilendiğinde) çağrılır; aynı karakter için tekrar çözmez. */
    fun bind(detail: KitsugiCharacterDetail, source: String, characterId: Int) {
        bindQuery(
            fallbackKey = "none:${source.lowercase()}:$characterId",
            queryProvider = { FandomCharacterQueries.from(detail, source, characterId) }
        )
    }

    /** Ekip/seslendirme sanatçısı detayı: kişi, çalıştığı yapımların wiki'sinde adıyla aranır. */
    fun bindStaff(detail: KitsugiStaffDetail, source: String, staffId: Int) {
        bindQuery(
            fallbackKey = "none:staff:${source.lowercase()}:$staffId",
            queryProvider = { FandomCharacterQueries.fromStaff(detail, source, staffId) }
        )
    }

    /**
     * Genel bağlama: [queryProvider] null dönerse sekme "bulunamadı" durumuna düşer.
     * Aynı sorgu + aynı tercihler için yeniden çözümleme yapılmaz.
     */
    fun bindQuery(fallbackKey: String, queryProvider: () -> FandomCharacterQuery?, forceRefresh: Boolean = false) {
        lastBinding = { queryProvider() to fallbackKey }
        val prefsState = FandomWikiPrefs.state(appContext)
        val query = queryProvider()
        val key = if (query == null) {
            fallbackKey
        } else {
            "${query.cacheKey()}|${prefsState.enabled}|${prefsState.nameFallback}|${prefsState.preferTurkish}"
        }
        if (key == boundKey && !forceRefresh) return
        boundKey = key
        if (lastRejectedBindingKey != fallbackKey) {
            lastRejectedBindingKey = null
            _lastRejected.value = null
        }
        currentQuery = query
        resolveJob?.cancel()
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
        _sections.value = emptyMap()
        _translations.value = emptyMap()

        if (query == null || !prefsState.enabled) {
            _state.value = UiState.Unavailable
            return
        }
        _state.value = UiState.Loading
        resolveJob = viewModelScope.launch {
            val page = try {
                FandomRepository.resolveCharacterPage(appContext, query, forceRefresh)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Wiki sayfası çözümlenemedi: ${e.message}")
                null
            }
            _state.value = if (page != null) UiState.Ready(page) else UiState.Unavailable
            if (page != null) onPageReady(page)
        }
    }

    /**
     * Hazır bir sayfa için: otomatik çeviri ve ilgili wiki sayfaları. Karakter bağlanınca ve
     * kaynak (Fandom ↔ Vikipedi) değişince aynı akış çalışır.
     */
    private fun onPageReady(page: FandomCharacterPage) {
        // Otomatik çeviri açıkken tüm metinler kendiliğinden çevrilir: özet + bilgi kutusu
        // (bölümler kullanıcı açtıkça, [loadSection] içinde çevrilir). Ayar suspend olduğu
        // için bu kontrol bir coroutine içinde yapılır; sayfa bu sırada değişmişse atlanır.
        viewModelScope.launch {
            if (!isCurrent(page) || !isAutoTranslateEnabled()) return@launch
            if (!page.description.isNullOrBlank()) translate(KEY_DESCRIPTION)
            if (!page.infobox?.allFields().isNullOrEmpty()) translateInfobox()
        }
        // İlgili wiki sayfalarını arka planda topla (ilk 1-2 bölümden viki içi bağlantılar).
        val wikiId = page.wikiId
        val pageTitle = page.pageTitle
        relatedJob?.cancel()
        _relatedPages.value = emptyList()
        relatedJob = viewModelScope.launch {
            val list = try {
                if (FandomUrls.isWikipedia(wikiId)) {
                    // Vikipedi kişi maddelerinde bilgi kutusu (doğum yeri, milliyet, meslek)
                    // bağlantıları "ilgili madde" sayılmaz; gövde metni + kısa tanım süzgeci
                    // kullanılır ve maddenin kendi adı önerilmez.
                    FandomRepository.loadWikipediaRelatedPages(
                        context = appContext,
                        wikiId = wikiId,
                        pageTitle = pageTitle,
                        selfTitles = listOf(pageTitle)
                    )
                } else {
                    val titles = LinkedHashSet<String>()
                    for (idx in listOf(0, 1)) {
                        val sec = FandomRepository.loadSection(appContext, wikiId, pageTitle, idx)
                        if (sec != null) titles += sec.links
                    }
                    if (titles.isNotEmpty()) {
                        FandomRepository.loadRelatedPages(appContext, wikiId, titles.take(30))
                    } else emptyList()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "İlgili wiki sayfaları yüklenemedi ($pageTitle): ${e.message}")
                emptyList()
            }
            val current = (_state.value as? UiState.Ready)?.page ?: return@launch
            if (current.wikiId == wikiId && current.pageTitle == pageTitle) {
                _relatedPages.value = list.take(20)
            }
        }
    }

    /**
     * Birden çok kaynak (Fandom sayfası + Vikipedi maddesi) varsa seçilen kaynağı etkin yapar.
     * [index] 0 = şu anki sayfa, sonrası [FandomCharacterPage.alternatives]. Sekmenin açık
     * bölümleri, çevirileri ve ilgili sayfaları yeni kaynağa göre sıfırlanır.
     */
    fun switchSource(index: Int) {
        val current = (_state.value as? UiState.Ready)?.page ?: return
        val next = current.withSourceAt(index) ?: return
        if (next.wikiId == current.wikiId && next.pageTitle == current.pageTitle) return
        cancelTranslations()
        relatedJob?.cancel()
        readerJob?.cancel()
        readerSectionJobs.values.forEach { it.cancel() }
        readerSectionJobs.clear()
        readerRelatedJob?.cancel()
        readerWebMode = true
        readerBrowserController.reset()
        _readerBrowser.value = FandomReaderBrowserState.Empty
        _relatedPages.value = emptyList()
        _reader.value = ReaderState.Closed
        _sections.value = emptyMap()
        _translations.value = emptyMap()
        _originalKeys.value = emptySet()
        _state.value = UiState.Ready(next)
        onPageReady(next)
    }

    private fun rebind(forceRefresh: Boolean = false) {
        val binding = lastBinding ?: return
        boundKey = null
        bindQuery(
            fallbackKey = binding().second,
            queryProvider = { binding().first },
            forceRefresh = forceRefresh
        )
    }

    /**
     * "Tekrar dene": çözümleme gerçekten baştan yapılır.
     *
     * Önceki davranış yalnızca yeniden bağlanıyordu; karakter ve yapım wiki katmanlarındaki
     * "bulunamadı" önbelleği (3 güne kadar) ve süreç içi "wiki yok" listesi aynı sonucu
     * anında geri verdiği için düğme ölü gibi görünüyordu. Artık zehir kayıtlar temizlenir
     * ve [bindQuery] çözümlemeyi `forceRefresh` ile (önbellek okumadan) çalıştırır.
     */
    fun retry() {
        viewModelScope.launch {
            try {
                FandomWikiResolver.forgetMissingWikis()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Eksik wiki listesi temizlenemedi: ${e.message}")
            }
            rebind(forceRefresh = true)
        }
    }

    /** Orijinal / çeviri geçişi: bu anahtar için orijinal metnin gösterilip gösterilmeyeceğini değiştirir. */
    fun toggleOriginal(key: String) {
        val wasOriginal = key in _originalKeys.value
        _originalKeys.update { if (key in it) it - key else it + key }
        // A user can tap Original before the section translation finishes. Switching
        // back must request the missing translation instead of leaving the raw text on
        // screen until a later unrelated recomposition.
        if (wasOriginal && !_translations.value.hasWikiTranslation(key)) {
            viewModelScope.launch {
                if (isAutoTranslateEnabled()) translate(key)
            }
        }
    }

    fun loadSection(index: Int) {
        val page = (_state.value as? UiState.Ready)?.page ?: return
        when (_sections.value[index]) {
            is SectionState.Loading, is SectionState.Loaded -> return
            else -> Unit
        }
        _sections.update { it + (index to SectionState.Loading) }
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
                Log.w(TAG, "Bölüm yüklenemedi ($index): ${e.message}")
                null
            }
            if (!isCurrent(page)) return@launch
            val newState: SectionState = if (content != null) SectionState.Loaded(content) else SectionState.Failed
            _sections.update { it + (index to newState) }
            if (content != null && !content.isEmpty() && isAutoTranslateEnabled()) translate(sectionKey(index))
        }
    }

    /**
     * Metni uygulama içi çeviriye verir. [force] = true iken eldeki çeviri yok sayılır
     * (hedef dil değiştiğinde ya da otomatik çeviri yeni açıldığında).
     */
    fun translate(key: String, force: Boolean = false) {
        if (key in _translating.value) return
        if (!force && key in _translations.value) return
        if (!force && key in _originalKeys.value) return
        val page = (_state.value as? UiState.Ready)?.page ?: return
        val reader = _reader.value as? ReaderState.Ready
        val readerLocation = reader?.let { FandomReaderLocation(it.wikiId, it.page.displayTitle) }

        // Kaynak zaten hedef dilde: "çeviri" metni değiştirmez, yalnızca kullanıcıya iki
        // farklı metin varmış izlenimi verir (Vikipedi TR maddesi + Türkçe hedef gibi).
        // Yapım sekmesiyle aynı davranış; kontrol yapılandırılmış bölümlerden ÖNCE yapılır.
        // Okuyucu bağlantıları başka bir vikiye (ör. Vikipedi TR) gidebilir; dil kontrolü
        // gösterilen SAYFANIN vikisine göre yapılır, kök karakter sayfasına göre değil.
        val sourceWikiId = if (FandomReaderTranslationKeys.isReaderKey(key) && readerLocation != null) {
            readerLocation.wikiId
        } else {
            page.wikiId
        }
        if (isTargetLanguageSource(sourceWikiId)) {
            translationJobs.remove(key)?.cancel()
            _translating.update { it - key }
            _originalKeys.update { it - key }
            clearTranslation(key)
            return
        }

        // İçindekiler satırları düz metin değil, sıralı başlık listesidir: kendi
        // yapı-korumalı akışı vardır (satır sayısı kanıtlanır, hiçbir satır kaymaz).
        if (readerLocation != null && key == FandomReaderTranslationKeys.contents(readerLocation)) {
            translateReaderContents(readerLocation, force)
            return
        }

        // YAPI KORUMASI: Bölüm içeriği düz metne indirilmez; blok blok çevrilir ve blok
        // anahtarlarına yazılır. Böylece liste/kart/başlık düzeni, görsel sırası ve
        // bağlantı hedefleri çeviri sonrasında da birebir aynı kalır.
        sectionStructure(key, reader, readerLocation)?.let { structure ->
            translateSectionStructured(key, structure, page, force)
            return
        }

        val text = when {
            key == KEY_DESCRIPTION -> page.description
            key.startsWith("sec:") -> {
                val index = key.removePrefix("sec:").toIntOrNull()
                (index?.let { _sections.value[it] } as? SectionState.Loaded)?.content?.plainText()
            }
            readerLocation != null && key == FandomReaderTranslationKeys.description(readerLocation) ->
                reader.page.description
            readerLocation != null -> reader.sections.entries.firstOrNull { (index, _) ->
                key == FandomReaderTranslationKeys.section(readerLocation, index)
            }?.value?.plainText()
            else -> null
        }
        if (text.isNullOrBlank()) return
        if (force) clearTranslation(key)
        _translating.update { it + key }
        val generation = translationGeneration
        translationJobs[key] = viewModelScope.launch {
            try {
                // Parça parça akan çeviri: detay sayfasındaki açıklama çevirisiyle aynı davranış.
                val result = withContext(Dispatchers.IO) {
                    translationManager.translateToTarget(text) { partial ->
                        if (generation == translationGeneration && partial.isNotBlank() && isCurrent(page)) {
                            _translations.update { it + (key to partial) }
                        }
                    }
                }
                if (generation != translationGeneration || !isCurrent(page)) return@launch
                if (result.isNotBlank() && result != text) {
                    _translations.update { it + (key to result) }
                } else {
                    // Metin zaten hedef dilde (ya da çeviri boş döndü): akan taslağı geri al.
                    clearTranslation(key)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Çeviri başarısız ($key): ${e.message}")
                if (generation == translationGeneration && isCurrent(page)) clearTranslation(key)
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
     * Kaynağın içerik dili çeviri hedefiyle aynı mı? (Örn. Vikipedi TR maddesi + Türkçe hedef.)
     * Aynı dildeki metni "çevirmek" içeriği değiştirmez; bu kaynaklarda uygulama içi çeviri
     * hiç başlatılmaz (yapım wiki sekmesiyle aynı kural).
     */
    private fun isTargetLanguageSource(wikiId: String): Boolean {
        val origin = if (FandomUrls.isWikipedia(wikiId)) FandomUrls.wikipediaLang(wikiId)
        else FandomUrls.languageOf(wikiId)
        val target = KitsugiTranslatePrefs.resolvedTarget()
        if (origin.isNullOrBlank() || target.isBlank()) return false
        return origin.equals(target.substringBefore('-'), ignoreCase = true)
    }

    /**
     * Okuyucu İçindekiler satırlarını TEK istekte çevirir.
     *
     * Satırlar `⟪§⟫` ayırıcı jetonuyla gönderilir ve dönen satır sayısı kanıtlanır; sayı
     * tutmazsa her başlık kendi başına çevrilir. Böylece hiçbir başlık yanlış bölümün
     * karşısına düşmez. Sonuç [FandomReaderTranslationKeys.contents] anahtarının çocukları
     * olarak saklanır.
     */
    private fun translateReaderContents(location: FandomReaderLocation, force: Boolean = false) {
        val page = (_state.value as? UiState.Ready)?.page ?: return
        val reader = _reader.value as? ReaderState.Ready ?: return
        val key = FandomReaderTranslationKeys.contents(location)
        if (!force && key in _translating.value) return
        if (!force && key in _translations.value) return
        if (!force && key in _originalKeys.value) return
        if (isTargetLanguageSource(location.wikiId)) return
        val titles = reader.page.sections.map { it.title.trim() }.filter { it.isNotEmpty() }
        if (titles.isEmpty()) return
        if (force) clearTranslation(key)
        _translating.update { it + key }
        val generation = translationGeneration
        translationJobs[key] = viewModelScope.launch {
            try {
                val translated = withContext(Dispatchers.IO) {
                    translateRowsPreservingStructure(titles) { row -> translationManager.translateToTarget(row) }
                }
                if (generation != translationGeneration || !isCurrent(page)) return@launch
                val active = _reader.value as? ReaderState.Ready ?: return@launch
                if (active.page.displayTitle != reader.page.displayTitle) return@launch
                val additions = HashMap<String, String>()
                translated.forEachIndexed { index, value ->
                    if (value.isNotBlank() && value != titles[index]) {
                        additions[WikiStructuredTranslationKeys.block(key, index)] = value
                    }
                }
                if (additions.isEmpty()) {
                    clearTranslation(key)
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
     * çeviri açıksa).
     */
    fun setReaderOriginal(showOriginal: Boolean) {
        val reader = _reader.value as? ReaderState.Ready ?: return
        if (reader.page.displayTitle.isBlank()) return
        val location = FandomReaderLocation(reader.wikiId, reader.page.displayTitle)
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

    /** Çeviriyi bırakıp orijinal metne döner. */
    private fun clearTranslation(key: String) {
        if (key == KEY_INFOBOX) {
            _translations.update { map -> map.filterKeys { it != KEY_INFOBOX && !it.startsWith(INFOBOX_FIELD_PREFIX) } }
        } else {
            // Bölüm çevirisinde blok çocukları da silinir; yarım kalan çeviri ekranda kalmaz.
            _translations.update { it.withoutTranslation(key) }
        }
    }

    /**
     * Anahtar bir bölüm içeriğine karşılık geliyorsa o içeriği döner; aksi hâlde `null`
     * (açıklama/özet gibi düz metin akışı kullanılır).
     */
    private fun sectionStructure(
        key: String,
        reader: ReaderState.Ready?,
        readerLocation: FandomReaderLocation?
    ): FandomSectionContent? {
        if (key.startsWith("sec:")) {
            val index = key.removePrefix("sec:").toIntOrNull() ?: return null
            return (_sections.value[index] as? SectionState.Loaded)?.content
        }
        if (reader == null || readerLocation == null) return null
        return reader.sections.entries
            .firstOrNull { (index, _) -> key == FandomReaderTranslationKeys.section(readerLocation, index) }
            ?.value
    }

    /**
     * Bölümü blok blok çevirir. Tamamlanan her blok anında arayüze akar; sonuçta bölüm
     * anahtarına "tamam" işareti yazılır. Hiçbir blok çevrilemezse (örn. metin zaten hedef
     * dilde) çeviri tamamen geri alınır ve orijinal hâliyle kalır.
     */
    private fun translateSectionStructured(
        key: String,
        content: FandomSectionContent,
        page: FandomCharacterPage,
        force: Boolean
    ) {
        if (force) clearTranslation(key)
        _translating.update { it + key }
        val generation = translationGeneration
        translationJobs[key] = viewModelScope.launch {
            fun isAlive(): Boolean = generation == translationGeneration && isCurrent(page)
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
                    clearTranslation(key)
                } else {
                    _translations.update { it + (entries + (key to WikiStructuredTranslationKeys.COMPLETE)) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Bölüm çevirisi başarısız ($key): ${e.message}")
                if (generation == translationGeneration && isCurrent(page)) clearTranslation(key)
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
     * Şu an bellekte olan tüm wiki metinlerini çevirir (açıklama + yüklenmiş bölümler +
     * bilgi kutusu). Otomatik çeviri ayarı açıldığında ya da hedef dil değiştiğinde çağrılır.
     */
    private fun retranslateLoadedTexts(force: Boolean) {
        val page = (_state.value as? UiState.Ready)?.page ?: return
        if (force) {
            cancelTranslations()
            _translations.value = emptyMap()
            _originalKeys.value = emptySet()
        }
        if (!page.description.isNullOrBlank()) translate(KEY_DESCRIPTION, force = force)
        if (!page.infobox?.allFields().isNullOrEmpty()) translateInfobox(force = force)
        _sections.value.keys.sorted().forEach { index -> translate(sectionKey(index), force = force) }

        val reader = _reader.value as? ReaderState.Ready
        if (reader != null) {
            val location = FandomReaderLocation(reader.wikiId, reader.page.displayTitle)
            if (!reader.page.description.isNullOrBlank()) {
                translate(FandomReaderTranslationKeys.description(location), force = force)
            }
            reader.sections.keys.sorted().forEach { index ->
                translate(FandomReaderTranslationKeys.section(location, index), force = force)
            }
            translate(FandomReaderTranslationKeys.contents(location), force = force)
        }
    }

    /**
     * Bilgi kutusundaki serbest metin değerleri topluca çevirir. Arayüz dilinde zaten karşılığı
     * olan kısa değerler ("Male", "Deceased"…) atlanır; sonuçlar alan sırasına göre saklanır.
     * Satır sayısı korunursa tek istekle, bozulursa alan alan çevrilir.
     */
    fun translateInfobox(force: Boolean = false) {
        if (KEY_INFOBOX in _translating.value) return
        if (!force && KEY_INFOBOX in _translations.value) return
        if (!force && KEY_INFOBOX in _originalKeys.value) return
        val page = (_state.value as? UiState.Ready)?.page ?: return
        val fields = page.infobox?.allFields().orEmpty()
        val targets = fields.withIndex()
            .filter { (_, field) ->
                field.value.isNotBlank() && FandomInfoboxLabels.valueResId(field.value) == null
            }
            .take(MAX_INFOBOX_FIELDS)
        if (targets.isEmpty()) {
            _translations.update { it + (KEY_INFOBOX to "1") }
            return
        }
        if (force) clearTranslation(KEY_INFOBOX)
        _translating.update { it + KEY_INFOBOX }
        val generation = translationGeneration
        translationJobs[KEY_INFOBOX] = viewModelScope.launch {
            try {
                val results = withContext(Dispatchers.IO) {
                    val singleLine = targets.map { it.value.value.replace(Regex("\\s*\\n\\s*"), " · ").trim() }
                    // YAPI KORUMASI: Satır sayısı kanıtlanır (⟪§⟫ ayırıcı jetonu). Google
                    // satırları birleştirir/bölerse tek istek terk edilir ve alanlar tek tek
                    // çevrilir; böylece etiket-değer eşleşmesi hiçbir koşulda kaymaz.
                    translateRowsPreservingStructure(singleLine) { row ->
                        translationManager.translateToTarget(row)
                    }
                }
                if (generation != translationGeneration || !isCurrent(page)) return@launch
                val additions = HashMap<String, String>()
                targets.forEachIndexed { position, (fieldIndex, field) ->
                    val translated = results.getOrNull(position).orEmpty()
                    if (translated.isNotBlank() && translated != field.value) {
                        additions[infoboxFieldKey(fieldIndex)] = translated
                    }
                }
                additions[KEY_INFOBOX] = "1"
                _translations.update { it + additions }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == translationGeneration) {
                    Log.w(TAG, "Bilgi kutusu çevirisi başarısız: ${e.message}")
                }
            } finally {
                if (generation == translationGeneration &&
                    translationJobs[KEY_INFOBOX] === kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]
                ) {
                    _translating.update { it - KEY_INFOBOX }
                    translationJobs.remove(KEY_INFOBOX)
                }
            }
        }
    }

    /** "Bu eşleşme doğru": otomatik eşleşme kesinleşir (wiki onayı aynı yapımın diğer karakterlerine de geçer). */
    fun confirmMatch() {
        val page = (_state.value as? UiState.Ready)?.page ?: return
        val query = currentQuery ?: return
        val confirmed = try {
            FandomRepository.confirmCharacterPage(appContext, query, page)
        } catch (e: Exception) {
            Log.w(TAG, "Onay kaydedilemedi: ${e.message}")
            return
        }
        if (isCurrent(page)) {
            _state.value = UiState.Ready(confirmed)
        }
    }

    /** "Bu eşleşme yanlış": sayfa bu karakter için bir daha önerilmez, yeniden çözülür. */
    fun reportWrongMatch() {
        val page = (_state.value as? UiState.Ready)?.page ?: return
        val query = currentQuery ?: return
        viewModelScope.launch {
            try {
                FandomRepository.rejectCharacterPage(appContext, query, page)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Bildirim kaydedilemedi: ${e.message}")
            }
            rebind()
            // `rebind()` senkron tamamlanır; kartı ondan sonra koymak bağlama sıfırlamasına
            // takılmadan (aynı anahtar) kartın yeni durumda da görünmesini sağlar.
            lastRejectedBindingKey = boundKey
            _lastRejected.value = page
        }
    }

    /** Geri alma kartındaki "Geri al": reddi kaldırır ve sayfayı tekrar çözümletir. */
    fun undoLastReject() {
        val rejected = _lastRejected.value ?: return
        val query = currentQuery ?: return
        viewModelScope.launch {
            try {
                FandomRepository.undoRejectCharacterPage(appContext, query, rejected)
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

    /** Aynı wiki sayfası hâlâ gösteriliyor mu? (Onay sonrası sayfa nesnesi değişir, kimliği değişmez.) */
    private fun isCurrent(page: FandomCharacterPage): Boolean {
        val current = (_state.value as? UiState.Ready)?.page ?: return false
        return current.wikiId == page.wikiId && current.pageTitle == page.pageTitle
    }

    private fun cancelTranslations() {
        translationGeneration += 1
        translationJobs.values.forEach { it.cancel() }
        translationJobs.clear()
        _translating.value = emptySet()
        // Yeni sayfa / otomatik çeviri kapandı: eski orijinal-geçiş durumu geçerli değil.
        _originalKeys.value = emptySet()
    }

    private suspend fun isAutoTranslateEnabled(): Boolean = try {
        settingsDataStore.settingsFlow.first().autoTranslateEnabled
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }
    // ── Uygulama içi wiki okuyucu ────────────────────────────────────────────

    /** Mevcut karakterin wiki sayfasını veya başka bir wiki sayfasını okuyucuda açar. */
    fun openReader(title: String? = null, wikiIdOverride: String? = null, pushToBackStack: Boolean = true) {
        val rootPage = (_state.value as? UiState.Ready)?.page ?: return
        val currentReader = _reader.value as? ReaderState.Ready
        val targetWiki = wikiIdOverride ?: currentReader?.wikiId ?: rootPage.wikiId
        val targetTitle = (title ?: currentReader?.page?.displayTitle ?: rootPage.pageTitle).trim()
        if (targetTitle.isEmpty()) return
        val location = FandomReaderLocation(targetWiki, targetTitle)
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
        readerJob?.cancel()
        readerSectionJobs.values.forEach { it.cancel() }
        readerSectionJobs.clear()
        readerRelatedJob?.cancel()
        _reader.value = ReaderState.Loading
        if (readerWebMode) return
        readerJob = viewModelScope.launch {
            val meta = try {
                FandomRepository.loadFullPageMeta(appContext, location.wikiId, location.title, forceRefresh)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Okuyucu sayfa yüklenemedi (${location.wikiId}/${location.title}): ${e.message}")
                null
            }
            if (readerBrowserController.activeLocation != location) return@launch
            if (meta == null) {
                _reader.value = ReaderState.Failed(location.title)
                return@launch
            }
            val resolvedLocation = location.copy(title = meta.displayTitle)
            readerBrowserController.normalizeCurrentLocation(resolvedLocation)
            _readerBrowser.value = readerBrowserController.snapshot
            _reader.value = ReaderState.Ready(
                wikiId = location.wikiId,
                page = meta,
                sections = emptyMap(),
                related = emptyList()
            )
            launchRelatedLoad(location.wikiId, meta)
            // The lead is section 0 and is NOT included in the TOC. Without this,
            // character/staff reader pages with no short description appear empty.
            loadReaderSection(0)
            if (isAutoTranslateEnabled()) {
                if (!meta.description.isNullOrBlank()) {
                    translate(FandomReaderTranslationKeys.description(resolvedLocation))
                }
                // İçindekiler satırları da seçili dile gelir; sayfa "bütün olarak" çevrilir.
                translateReaderContents(resolvedLocation)
            }
        }
    }

    private fun launchRelatedLoad(wikiId: String, page: FandomFullPage) {
        readerRelatedJob?.cancel()
        readerRelatedJob = viewModelScope.launch {
            val related = try {
                if (FandomUrls.isWikipedia(wikiId)) {
                    // Vikipedi kişi maddelerinde bilgi kutusu (doğum yeri, milliyet, meslek)
                    // bağlantıları "ilgili madde" değildir; gövde metni kullanılır.
                    FandomRepository.loadWikipediaRelatedPages(
                        context = appContext,
                        wikiId = wikiId,
                        pageTitle = page.displayTitle,
                        selfTitles = listOf(page.displayTitle)
                    )
                } else {
                    val candidates = LinkedHashSet<String>()
                    for (idx in listOf(0, 1)) {
                        val sec = FandomRepository.loadSection(appContext, wikiId, page.displayTitle, idx)
                        if (sec != null) candidates += sec.links
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
            if (cur.page.displayTitle != page.displayTitle || cur.wikiId != wikiId) return@launch
            _reader.value = cur.copy(related = related.take(20))
        }
    }

    /** Okuyucu: açılan bir İçindekiler bölümünü yükler; birden çok bölüm paralel açılabilir. */
    fun loadReaderSection(index: Int) {
        val cur = _reader.value as? ReaderState.Ready ?: return
        if (cur.sections.containsKey(index) || index in cur.loadingSections) return
        _reader.value = cur.copy(
            loadingSections = cur.loadingSections + index,
            failedSections = cur.failedSections - index
        )
        val job = viewModelScope.launch {
            try {
                val content = try {
                    FandomRepository.loadSection(appContext, cur.wikiId, cur.page.displayTitle, index)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Okuyucu bölüm yüklenemedi (${cur.page.displayTitle}#$index): ${e.message}")
                    null
                }
                val now = _reader.value as? ReaderState.Ready ?: return@launch
                if (now.page.displayTitle != cur.page.displayTitle || now.wikiId != cur.wikiId) return@launch
                if (content == null) {
                    _reader.value = now.copy(
                        loadingSections = now.loadingSections - index,
                        failedSections = now.failedSections + index
                    )
                    return@launch
                }
                val sections = HashMap(now.sections)
                sections[index] = content
                _reader.value = now.copy(
                    sections = sections,
                    loadingSections = now.loadingSections - index,
                    failedSections = now.failedSections - index
                )
                if (!content.isEmpty() && isAutoTranslateEnabled()) {
                    translate(FandomReaderTranslationKeys.section(FandomReaderLocation(cur.wikiId, cur.page.displayTitle), index))
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
     * İlgili sayfa kartı / metin içi bağlantı: Chrome/Brave gibi aktif sekmenin hemen
     * sağında YENİ sekmede açılır (kaynak sayfa yerinde kalır). Aynı sayfa zaten açık
     * bir sekmedeyse ona geçilir; sekme sınırı doluysa bağlantı ölü doğmasın diye aktif
     * sekmede geçmişe eklenerek açılır.
     */
    fun openReaderLink(title: String) {
        val rootPage = (_state.value as? UiState.Ready)?.page ?: return
        val currentReader = _reader.value as? ReaderState.Ready
        val targetTitle = title.trim()
        if (targetTitle.isEmpty()) return
        val location = FandomReaderLocation(
            currentReader?.wikiId ?: rootPage.wikiId,
            targetTitle
        )
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

}
