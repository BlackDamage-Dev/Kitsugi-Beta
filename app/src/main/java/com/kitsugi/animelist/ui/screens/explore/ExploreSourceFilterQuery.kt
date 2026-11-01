package com.kitsugi.animelist.ui.screens.explore

import com.kitsugi.animelist.data.auth.BangumiApiClient

import com.kitsugi.animelist.data.remote.JikanApiClient
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.data.remote.KitsuExploreClient
import com.kitsugi.animelist.data.remote.KitsugiBangumiClient
import com.kitsugi.animelist.data.remote.KitsugiShikimoriClient
import com.kitsugi.animelist.data.remote.SimklApiClient
import com.kitsugi.animelist.data.remote.TmdbApiClient
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.ui.components.KITSUGI_MEDIA_GENRES
import com.kitsugi.animelist.ui.components.KitsugiGridSortOption
import com.kitsugi.animelist.ui.screens.search.CanonicalFilterBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Keşfet "Tümünü Gör" sayfalarının (bkz. [com.kitsugi.animelist.ui.screens.fullscreen.FullScreenMediaGridPage])
 * **kaynak tarafında** filtrelenmesi ve sıralanması.
 *
 * ### Neden bu dosya var?
 * Sayfa, filtre/seçim değiştiğinde yalnızca elindeki listeyi yerelde süzüp sıralıyordu. Kaydırma
 * başına gelen yeni sayfa da aynı yerel işleme giriyordu; bu yüzden:
 *  - "Tür Filtresi" seçildiğinde kaynak API'si filtresiz sayfa döndürmeye devam ediyor,
 *    sayfa gövdesi boşalıyor ve otomatik yükleme boş yere sayfa çekiyordu (döngü / şişme),
 *  - "Puan: Yüksekten Düşüğe" gibi bir sıralama yalnızca **yüklenmiş** sayfalar içinde
 *    geçerli oluyor, yeni sayfa gelince liste baştan karışıyordu.
 *
 * Bu motor, seçilen tür ve sıralamayı her kaynağın kendi API parametrelerine çevirip isteği
 * doğrudan filtreli gönderir. Böylece sayfa 1, 2, 3… aynı sıralamayı korur ve elde tutulan
 * liste büyüdükçe sıralama bozulmaz.
 *
 * ### Kaynak yetenekleri
 * Kaynak API'lerinin tamamı her tür/sıralamayı desteklemez (örn. Simkl'in tür uç noktasında
 * puan sıralaması, Shikimori'de artan puan sıralaması, Bangumi'de başlık sıralaması yoktur).
 * [ExploreSourceFilters] hangi seçeneğin sunucuda uygulanabildiğini bildirir; sayfa ve filtre
 * sayfası bu bilgiyi kullanır. Desteklenmeyen seçenekler eskisi gibi cihazda uygulanır ve
 * filtre sayfasında "📱 yerel" olarak işaretlenir.
 */
object ExploreSourceFilters {

    /** Kaynağa gönderilebilecek tür kimlikleri (filtre sayfası rozetleri için). */
    fun serverGenreIds(platform: ExplorePlatform, categoryType: ExploreCategoryType): Set<String> =
        GENRE_TERMS.keys.filter { supportsGenre(platform, categoryType, it) }.toSet()

    /** Kaynağa gönderilebilecek sıralama seçenekleri (filtre sayfası rozetleri için). */
    fun serverSorts(
        platform: ExplorePlatform,
        categoryType: ExploreCategoryType,
        genreId: String = "ALL"
    ): Set<KitsugiGridSortOption> {
        // Bangumi'nin sıralama desteği **tür seçiliyken** açılır (`filter.tag` ile arama
        // ucu); bu yüzden `SORT_SUPPORT[BANGUMI]` bilinçli olarak boştur. Boş kümeden
        // filtrelemek yüzünden `serverSorts(BANGUMI, …, genre)` her zaman boş dönüyor,
        // filtre sayfası da sunucunun gerçekten uygulayabildiği Puan/Popülerlik
        // sıralamalarını "📱 yerel" diye işaretliyordu.
        val base = if (platform == ExplorePlatform.BANGUMI) {
            BANGUMI_SEARCH_SORTS
        } else {
            SORT_SUPPORT[platform].orEmpty()
        }
        return base.filterTo(mutableSetOf()) {
            supportsSort(platform, categoryType, it, genreId)
        }
    }

    /**
     * Seçilen türün kaynak API'sinde gerçekten bir karşılığı var mı?
     * Örn. TMDB'de "Psikolojik" türü yoktur, Simkl'de "Mecha" türü yoktur.
     */
    fun supportsGenre(
        platform: ExplorePlatform,
        categoryType: ExploreCategoryType,
        genreId: String
    ): Boolean {
        val term = GENRE_TERMS[genreId] ?: return false
        if (platform == ExplorePlatform.ALL) return true
        val spec = shelfSpec(platform, categoryType) ?: return false
        return when (platform) {
            // AniList resmî türler + etiket taksonomisi ile her terimi karşılar.
            ExplorePlatform.AniList -> true
            ExplorePlatform.MAL -> CanonicalFilterBridge.mapMalGenreId(term, spec.mediaType == MediaType.Manga) != null
            ExplorePlatform.TMDB -> spec.tmdbKinds.any { isMovie ->
                CanonicalFilterBridge.mapTmdbGenreId(term, !isMovie) != null
            }
            ExplorePlatform.KITSU -> CanonicalFilterBridge.mapSimklGenre(term) != null
            ExplorePlatform.SHIKIMORI -> CanonicalFilterBridge.mapAnimeGenreId(term) != null ||
                CanonicalFilterBridge.mapMalGenreId(term, spec.mediaType == MediaType.Manga) != null
            ExplorePlatform.SIMKL -> CanonicalFilterBridge.mapSimklGenre(term) != null
            // Bangumi etiket araması yerel sözlükle çalışır; eşlenemeyen çip gönderilmez.
            // 3,18 MB'lık `bangumi/tags.tsv` sözlüğü 19 girişlik eski haritadan çok daha
            // geniş bir kapsama sahiptir: "Aksiyon", "Bilim Kurgu", "Korku", "Spor" gibi
            // çipler artık sunucunun anladığı Çince etikete çevrilebiliyor.
            ExplorePlatform.BANGUMI -> bangumiTagFor(term) != null
            ExplorePlatform.ALL -> true
        }
    }

    /**
     * Seçilen sıralamanın kaynak API'sinde karşılığı var mı?
     *
     * [genreId] yalnız Bangumi için önemlidir: etiketli (tür seçili) istek arama ucuna gider ve
     * sıralama parametresini kabul eder; etiketsiz gezinti ucu sıralamayı yok sayar.
     */
    fun supportsSort(
        platform: ExplorePlatform,
        categoryType: ExploreCategoryType,
        sort: KitsugiGridSortOption,
        genreId: String = "ALL"
    ): Boolean {
        if (sort == KitsugiGridSortOption.DEFAULT) return true
        if (platform == ExplorePlatform.ALL) return true
        if (shelfSpec(platform, categoryType) == null) return false
        if (platform == ExplorePlatform.BANGUMI) {
            // Bangumi'de `score`/`heat` sıralaması **boş sorguda** da çalışır: arama ucu
            // `sort=score|heat` + `rating_count` süzgecini kabul eder (probe A2/A5/F1/F4).
            // Eskiden tür seçili değilse sıralama "yerel" sayılıyor ve kullanıcı seçtiği
            // sıralamanın sunucuda uygulanmadığını görüyordu.
            return sort in BANGUMI_SEARCH_SORTS
        }
        return SORT_SUPPORT[platform].orEmpty().contains(sort)
    }

    /**
     * Filtre seçilmediğinde (veya "Varsayılan Sıralama" seçildiğinde) rafın doğal sırası.
     * Raf "En İyi/En Yüksek Puanlı" ise puana, "Yeni Eklenen" ise yeniye, diğerleri popülerliğe düşer.
     */
    fun defaultSort(platform: ExplorePlatform, categoryType: ExploreCategoryType): KitsugiGridSortOption =
        when (categoryType) {
            ExploreCategoryType.TOP_RATED_ANIME,
            ExploreCategoryType.TOP_RATED_MANGA -> KitsugiGridSortOption.SCORE_DESC
            // MAL/Shikimori/Bangumi "Popüler" rafları kendi sıralama API'lerinde puan/rank tabanlıdır.
            ExploreCategoryType.TOP_ANIME,
            ExploreCategoryType.TOP_MANGA -> when (platform) {
                ExplorePlatform.MAL,
                ExplorePlatform.SHIKIMORI,
                ExplorePlatform.BANGUMI -> KitsugiGridSortOption.SCORE_DESC
                else -> KitsugiGridSortOption.POPULARITY_DESC
            }
            ExploreCategoryType.NEWLY_ADDED_ANIME,
            ExploreCategoryType.NEWLY_ADDED_MANGA -> KitsugiGridSortOption.POPULARITY_DESC
            else -> KitsugiGridSortOption.POPULARITY_DESC
        }
}

/** Kaynak istemcileri — sayfa bunları hatırlar, her istekte yeniden kurulmaz. */
data class ExploreFilterClients(
    val jikan: JikanApiClient = JikanApiClient(),
    val tmdb: TmdbApiClient = TmdbApiClient(),
    val simkl: SimklApiClient = SimklApiClient()
) {
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}

/**
 * Filtreli/sıralı keşfet sayfası getirir.
 *
 * [genreId] "ALL" iken yalnız sıralama uygulanır; [sort] `DEFAULT` iken rafın doğal sıralaması
 * kullanılır. Kaynak bu isteği hiçbir şekilde karşılayamıyorsa boş liste döner — çağıran taraf
 * (sayfa) hangi durumda bu fonksiyonu çağırdığını [ExploreSourceFilters] ile doğrular.
 */
suspend fun fetchFilteredExplorePage(
    platform: ExplorePlatform,
    categoryType: ExploreCategoryType,
    genreId: String,
    sort: KitsugiGridSortOption,
    page: Int,
    showAdultContent: Boolean,
    clients: ExploreFilterClients,
    seasonalSeason: String? = null,
    seasonalYear: Int? = null,
    seasonalSort: String? = null
): List<JikanSearchResult> {
    if (page < 1) return emptyList()
    val term = if (genreId != "ALL") GENRE_TERMS[genreId] else null
    val effectiveSort = resolveEffectiveSort(platform, categoryType, sort, seasonalSort)
    val season = seasonalSeason?.takeIf { it.isNotBlank() }
    val year = seasonalYear?.takeIf { it > 0 }
    val isSeasonal = categoryType == ExploreCategoryType.SEASONAL_ANIME

    if (platform == ExplorePlatform.ALL) {
        return fetchAllSourcesPage(
            categoryType = categoryType,
            genreId = genreId,
            sort = effectiveSort,
            page = page,
            showAdultContent = showAdultContent,
            clients = clients,
            seasonalSeason = season,
            seasonalYear = year,
            seasonalSort = seasonalSort
        )
    }

    val spec = shelfSpec(platform, categoryType) ?: return emptyList()
    return runCatching {
        when (platform) {
            ExplorePlatform.AniList -> aniListPage(spec, term, effectiveSort, page, showAdultContent, clients, season, year, isSeasonal)
            ExplorePlatform.MAL -> malPage(spec, term, effectiveSort, page, showAdultContent, clients, season, year, isSeasonal)
            ExplorePlatform.TMDB -> tmdbPage(spec, term, effectiveSort, page, showAdultContent, clients)
            ExplorePlatform.KITSU -> kitsuPage(spec, term, effectiveSort, page)
            ExplorePlatform.SHIKIMORI -> shikimoriPage(spec, term, effectiveSort, page, showAdultContent)
            ExplorePlatform.SIMKL -> simklPage(spec, effectiveSort, page, term, clients)
            // Mevsimsel ızgarada seçilen mevsim **filtreye de** taşınır: tür çipi seçildiğinde
            // sunucu yoluna geçildiği için mevsim sessizce düşüyor ve "Kış 2025" yerine tüm
            // zamanların sonuçları geliyordu (R23).
            ExplorePlatform.BANGUMI -> bangumiPage(
                spec = spec,
                categoryType = categoryType,
                term = term,
                sort = effectiveSort,
                page = page,
                showAdultContent = showAdultContent,
                seasonalSeason = season.takeIf { isSeasonal },
                seasonalYear = year.takeIf { isSeasonal }
            )
            ExplorePlatform.ALL -> emptyList()
        }
    }.getOrElse { error ->
        if (error is kotlinx.coroutines.CancellationException) throw error
        emptyList()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Kaynak uygulamaları
// ─────────────────────────────────────────────────────────────────────────────

private suspend fun aniListPage(
    spec: ExploreShelfSpec,
    term: String?,
    sort: KitsugiGridSortOption,
    page: Int,
    showAdultContent: Boolean,
    clients: ExploreFilterClients,
    season: String?,
    year: Int?,
    isSeasonal: Boolean
): List<JikanSearchResult> {
    val isManga = spec.mediaType == MediaType.Manga
    // Filtre çiplerinin terimleri küçük harflidir ("slice of life"); AniList ise kanonik yazım
    // bekler ("Slice of Life", "Sci-Fi"). Önce kanonikleştir, sonra resmî tür / etiket ayrımı yap.
    val termName = term?.let { ENGLISH_TERM_CANONICAL[it.lowercase()] ?: it }
    val officialGenre = termName?.takeIf { CanonicalFilterBridge.isOfficialAniListGenre(it) }
    val tagGenre = termName?.takeIf { officialGenre == null }
    val countries: List<String?> = spec.aniListCountries?.map { it } ?: listOf(null)
    val merged = mutableListOf<JikanSearchResult>()
    for (country in countries) {
        merged += clients.jikan.searchAniListPaged(
            query = "",
            mediaType = spec.mediaType,
            showAdultContent = showAdultContent,
            // Formatsız (tür/sıralama dışında hiç facet yokken) istek boşa düşmesin diye tüm
            // formatlar geçilir; liste zaten mediaType ile sınırlıdır.
            formats = spec.aniListFormats ?: aniListAllFormats(isManga),
            statuses = spec.aniListStatuses,
            season = if (isSeasonal) season?.lowercase() else null,
            seasonYear = if (isSeasonal) year else null,
            genres = officialGenre?.let { listOf(it) },
            tags = tagGenre?.let { listOf(it) },
            sort = listOf(aniListSort(sort)),
            country = country,
            page = page,
            perPage = ANILIST_PAGE_SIZE
        ).results
    }
    return merged.distinctBy { it.exploreIdentity() }
}

private suspend fun malPage(
    spec: ExploreShelfSpec,
    term: String?,
    sort: KitsugiGridSortOption,
    page: Int,
    showAdultContent: Boolean,
    clients: ExploreFilterClients,
    season: String?,
    year: Int?,
    isSeasonal: Boolean
): List<JikanSearchResult> {
    val isManga = spec.mediaType == MediaType.Manga
    val malGenre = term?.let { CanonicalFilterBridge.mapMalGenreId(it, isManga)?.toIntOrNull() }
    val order = malOrder(sort) ?: return emptyList()
    // Yalnız sıralama istendiğinde MAL'ın resmî sıralama ucu puan/popülerlik için yeterlidir;
    // tarih/başlık sıraları ise Jikan'ın liste ucunda uygulanır.
    val forceListEndpoint = malGenre == null &&
        (order.first == "title" || order.first == "start_date")
    // Bir raf birden fazla MAL formatını birleştiriyorsa (roman → novel + lightnovel,
    // manhwa/manhua) her format için ayrı sayfa çekilir ve tekilleştirilir.
    val formats: List<String?> = spec.malFormats ?: listOf(null)
    val merged = formats.flatMap { format ->
        clients.jikan.searchMalAdvanced(
            query = "",
            mediaType = spec.mediaType,
            showAdultContent = showAdultContent,
            status = spec.malStatus,
            format = format,
            genres = malGenre?.let { listOf(it) },
            sort = order.second,
            orderBy = order.first,
            page = page,
            season = if (isSeasonal) season?.lowercase() else null,
            seasonYear = if (isSeasonal) year else null,
            applySortWithoutFacets = forceListEndpoint
        )
    }
    return merged.distinctBy { it.exploreIdentity() }
}

private suspend fun tmdbPage(
    spec: ExploreShelfSpec,
    term: String?,
    sort: KitsugiGridSortOption,
    page: Int,
    showAdultContent: Boolean,
    clients: ExploreFilterClients
): List<JikanSearchResult> {
    if (spec.tmdbKinds.isEmpty()) return emptyList()
    val merged = mutableListOf<JikanSearchResult>()
    for (isMovie in spec.tmdbKinds) {
        val mapped = term?.let { CanonicalFilterBridge.mapTmdbGenreId(it, !isMovie) }
        // Tür yalnızca bir içerik ailesinde varsa (örn. "Korku" yalnız filmlerde) diğeri atlanır.
        if (term != null && mapped == null) continue
        val sortBy = tmdbSort(sort, isMovie) ?: continue
        val genres = buildList {
            if (spec.tmdbAnimeFocused) add(TMDB_ANIMATION_GENRE)
            if (mapped != null) add(mapped)
        }.distinct()
        merged += runCatching {
            clients.tmdb.discoverAdvanced(
                isMovie = isMovie,
                page = page,
                sortBy = sortBy,
                genres = genres.takeIf { it.isNotEmpty() },
                originCountry = if (spec.tmdbAnimeFocused) "JP" else null,
                includeAdult = showAdultContent
            )
        }.getOrElse { error ->
            if (error is kotlinx.coroutines.CancellationException) throw error
            emptyList()
        }
    }
    return merged.distinctBy { it.exploreIdentity() }
}

private suspend fun kitsuPage(
    spec: ExploreShelfSpec,
    term: String?,
    sort: KitsugiGridSortOption,
    page: Int
): List<JikanSearchResult> = KitsuExploreClient.searchMediaAdvanced(
    mediaType = spec.mediaType,
    query = "",
    page = page,
    limit = KITSU_PAGE_SIZE,
    sort = kitsuSort(sort),
    subtypes = spec.kitsuSubtypes,
    statuses = spec.kitsuStatuses,
    categories = term
        ?.let(CanonicalFilterBridge::mapSimklGenre)
        ?.let(::kitsuCategorySlug)
        ?.let { listOf(it) }
)

private suspend fun shikimoriPage(
    spec: ExploreShelfSpec,
    term: String?,
    sort: KitsugiGridSortOption,
    page: Int,
    showAdultContent: Boolean
): List<JikanSearchResult> {
    val isManga = spec.mediaType == MediaType.Manga
    val genreId = term?.let {
        CanonicalFilterBridge.mapAnimeGenreId(it)
            ?: CanonicalFilterBridge.mapMalGenreId(it, isManga)?.toIntOrNull()
    }
    return KitsugiShikimoriClient.searchMediaAdvanced(
        mediaType = spec.mediaType,
        query = "",
        page = page,
        limit = SHIKIMORI_PAGE_SIZE,
        order = shikimoriOrder(sort),
        kinds = spec.shikimoriKinds,
        statuses = spec.shikimoriStatuses,
        genres = genreId?.let { listOf(it) },
        censored = !showAdultContent
    )
}

private suspend fun simklPage(
    spec: ExploreShelfSpec,
    sort: KitsugiGridSortOption,
    page: Int,
    term: String?,
    clients: ExploreFilterClients
): List<JikanSearchResult> {
    // Simkl tür uç noktası en fazla 20 sayfa verir.
    if (page > SIMKL_MAX_PAGE) return emptyList()
    return clients.simkl.searchAdvanced(
        type = spec.simklType,
        query = "",
        subtype = spec.simklSubtype,
        genre = term?.let { CanonicalFilterBridge.mapSimklGenre(it) },
        sort = simklSort(sort),
        limit = SIMKL_PAGE_SIZE,
        page = page
    )
}

private suspend fun bangumiPage(
    spec: ExploreShelfSpec,
    categoryType: ExploreCategoryType,
    term: String?,
    sort: KitsugiGridSortOption,
    page: Int,
    showAdultContent: Boolean,
    seasonalSeason: String? = null,
    seasonalYear: Int? = null
): List<JikanSearchResult> {
    val tag = term?.let { bangumiTagFor(it) }
    // Arama ucu sıralamaları BangumiApiClient.SearchSort sabitleriyle eşleşir;
    // `score` (oy sayısı tabanlı), `heat` (koleksiyon sayısı) ve `rank` (varsayılan).
    // Boş sorguda da çalışır (canlı prob A2/A5/F1/F4). Eskiden tür seçili değilse sıralama
    // zorla `rank`e çevriliyordu (R17).
    val effectiveSort = when (sort) {
        KitsugiGridSortOption.SCORE_DESC -> com.kitsugi.animelist.data.auth.BangumiApiClient.SearchSort.SCORE
        KitsugiGridSortOption.POPULARITY_DESC -> com.kitsugi.animelist.data.auth.BangumiApiClient.SearchSort.HEAT
        else -> com.kitsugi.animelist.data.auth.BangumiApiClient.SearchSort.RANK
    }
    // "Bangumi Dizileri" ve "Bangumi Filmleri" raflarında yalnızca 三次元 (type = 6)
    // sorulmalı. Aksi hâlde sayfalama `MediaType.TvShow` için tüm `type=6` yerine varsayılan
    // anime türünü arıyor, sayfalamasında dizi rafının altında anime, film rafının altında
    // anime filmleri de listeleniyordu (raf başlığı ile içerik uyuşmuyordu).
    // `MediaType.Movie` normalde "anime filmi + gerçek film" demektir; ama bu ızgara
    // "Bangumi Filmleri" rafının devamıdır → yalnızca 三次元 (type = 6).
    val subjectTypes = when (spec.mediaType) {
        MediaType.TvShow, MediaType.Movie -> listOf(BangumiApiClient.SubjectType.REAL)
        else -> null
    }
    // `type = 6` hem dizileri hem filmleri kapsar; raf başlığı ile içeriğin uyuşması için
    // 三次元 kayıtları Bangumi'nin kendi `meta_tags` değeriyle ayrılır (probe F1/F2).
    // `type = 1` (书籍) ise manga + roman + 画集 karışık gelir; "Tümünü Gör" manga ızgarası
    // roman listeliyordu, `漫画` meta etiketi bunu sunucuda ayırır (probe Q1–Q3).
    // Roman rafı ayrı ele alınır: `type = 1` kataloğunda roman `meta_tags=小说` ile ayrılır ve
    // `comicsOnly` (varsayılan `true`) manga meta etiketini ekleyip listeyi **boşaltır**.
    val isNovels = categoryType == ExploreCategoryType.NOVELS
    val metaTags = when {
        isNovels -> listOf("小说")
        spec.mediaType == MediaType.TvShow -> listOf("电视剧")
        spec.mediaType == MediaType.Movie -> listOf("电影")
        spec.mediaType == MediaType.Manga -> listOf("漫画")
        else -> emptyList()
    }
    return KitsugiBangumiClient.searchMediaAdvanced(
        mediaType = spec.mediaType,
        query = "",
        page = page,
        limit = BANGUMI_PAGE_SIZE,
        sort = effectiveSort,
        tags = listOfNotNull(tag),
        includeAdult = showAdultContent,
        subjectTypes = subjectTypes,
        comicsOnly = !isNovels,
        metaTags = metaTags,
        // Mevsim → `air_date` çeyreği (probe V1–V5). Yıl aralığı yerine çeyrek aralık
        // gönderilir; sunucu ikisini birlikte alsa da çeyrek daha dar olduğu için kazanır.
        season = seasonalSeason,
        seasonYear = seasonalYear
    )
}

/**
 * "Tümü" modu: seçilen türü/sıralamayı **destekleyen bütün kaynaklara** paralel istek atar ve
 * sonuçları kaynaklar arasında dönüşümlü (round-robin) birleştirir ki tek bir kaynak listeyi
 * ele geçirmesin. Türü desteklemeyen kaynak hiç sorgulanmaz (filtresiz içerik sızmaz).
 */
private suspend fun fetchAllSourcesPage(
    categoryType: ExploreCategoryType,
    genreId: String,
    sort: KitsugiGridSortOption,
    page: Int,
    showAdultContent: Boolean,
    clients: ExploreFilterClients,
    seasonalSeason: String?,
    seasonalYear: Int?,
    seasonalSort: String?
): List<JikanSearchResult> = coroutineScope {
    val targets = ExplorePlatform.sources.filter { source ->
        if (shelfSpec(source, categoryType) == null) return@filter false
        val genreOk = genreId == "ALL" || ExploreSourceFilters.supportsGenre(source, categoryType, genreId)
        val sortOk = sort == KitsugiGridSortOption.DEFAULT ||
            ExploreSourceFilters.supportsSort(source, categoryType, sort)
        genreOk && sortOk
    }
    if (targets.isEmpty()) return@coroutineScope emptyList()
    val perSource = targets.map { source ->
        async(Dispatchers.IO) {
            runCatching {
                fetchFilteredExplorePage(
                    platform = source,
                    categoryType = categoryType,
                    genreId = genreId,
                    sort = sort,
                    page = page,
                    showAdultContent = showAdultContent,
                    clients = clients,
                    seasonalSeason = seasonalSeason,
                    seasonalYear = seasonalYear,
                    seasonalSort = seasonalSort
                )
            }.getOrElse { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                emptyList()
            }
        }
    }.awaitAll()
    interleaveSources(perSource).distinctBy { it.exploreIdentity() }
}

/** Kaynak listelerini sırayla birer öğe alarak karıştırır (1. kaynak öne çıkmasın). */
private fun interleaveSources(lists: List<List<JikanSearchResult>>): List<JikanSearchResult> {
    val usable = lists.filter { it.isNotEmpty() }
    if (usable.isEmpty()) return emptyList()
    val out = ArrayList<JikanSearchResult>(usable.sumOf { it.size })
    var index = 0
    while (true) {
        var added = false
        for (list in usable) {
            if (index < list.size) {
                out += list[index]
                added = true
            }
        }
        if (!added) break
        index++
    }
    return out
}

// ─────────────────────────────────────────────────────────────────────────────
// Katalog eşleşmeleri (tür → kaynak parametresi)
// ─────────────────────────────────────────────────────────────────────────────

private fun aniListSort(sort: KitsugiGridSortOption): String = when (sort) {
    KitsugiGridSortOption.SCORE_DESC -> "SCORE_DESC"
    KitsugiGridSortOption.SCORE_ASC -> "SCORE"
    KitsugiGridSortOption.POPULARITY_DESC -> "POPULARITY_DESC"
    KitsugiGridSortOption.YEAR_DESC -> "START_DATE_DESC"
    KitsugiGridSortOption.YEAR_ASC -> "START_DATE"
    KitsugiGridSortOption.TITLE_ASC -> "TITLE_ROMAJI"
    KitsugiGridSortOption.TITLE_DESC -> "TITLE_ROMAJI_DESC"
    KitsugiGridSortOption.DEFAULT -> "POPULARITY_DESC"
}

private fun aniListAllFormats(isManga: Boolean): List<String> = if (isManga) {
    listOf("MANGA", "NOVEL", "ONE_SHOT")
} else {
    listOf("TV", "TV_SHORT", "MOVIE", "SPECIAL", "OVA", "ONA", "MUSIC")
}

private fun malOrder(sort: KitsugiGridSortOption): Pair<String, String>? = when (sort) {
    KitsugiGridSortOption.SCORE_DESC -> "score" to "desc"
    KitsugiGridSortOption.SCORE_ASC -> null // MAL/Jikan artan puan sıralaması sunmuyor
    KitsugiGridSortOption.POPULARITY_DESC -> "popularity" to "desc"
    KitsugiGridSortOption.YEAR_DESC -> "start_date" to "desc"
    KitsugiGridSortOption.YEAR_ASC -> "start_date" to "asc"
    KitsugiGridSortOption.TITLE_ASC -> "title" to "asc"
    KitsugiGridSortOption.TITLE_DESC -> "title" to "desc"
    KitsugiGridSortOption.DEFAULT -> "popularity" to "desc"
}

private fun tmdbSort(sort: KitsugiGridSortOption, isMovie: Boolean): String? = when (sort) {
    KitsugiGridSortOption.SCORE_DESC -> "vote_average.desc"
    KitsugiGridSortOption.SCORE_ASC -> "vote_average.asc"
    KitsugiGridSortOption.POPULARITY_DESC -> "popularity.desc"
    KitsugiGridSortOption.YEAR_DESC -> if (isMovie) "primary_release_date.desc" else "first_air_date.desc"
    KitsugiGridSortOption.YEAR_ASC -> if (isMovie) "primary_release_date.asc" else "first_air_date.asc"
    // TMDB dizi keşfinde başlık sıralaması belgelenmemiş → yalnız filmlerde.
    KitsugiGridSortOption.TITLE_ASC -> if (isMovie) "original_title.asc" else null
    KitsugiGridSortOption.TITLE_DESC -> if (isMovie) "original_title.desc" else null
    KitsugiGridSortOption.DEFAULT -> "popularity.desc"
}

private fun kitsuSort(sort: KitsugiGridSortOption): String = when (sort) {
    KitsugiGridSortOption.SCORE_DESC -> "-averageRating"
    KitsugiGridSortOption.SCORE_ASC -> "averageRating"
    KitsugiGridSortOption.POPULARITY_DESC -> "-userCount"
    KitsugiGridSortOption.YEAR_DESC -> "-startDate"
    KitsugiGridSortOption.YEAR_ASC -> "startDate"
    KitsugiGridSortOption.TITLE_ASC -> "canonicalTitle"
    KitsugiGridSortOption.TITLE_DESC -> "-canonicalTitle"
    KitsugiGridSortOption.DEFAULT -> "-userCount"
}

/** Kitsu kategori süzgeci slug bekler: "slice of life" → "slice-of-life". */
private fun kitsuCategorySlug(term: String): String =
    term.trim().lowercase().replace(" ", "-")

private fun shikimoriOrder(sort: KitsugiGridSortOption): String = when (sort) {
    KitsugiGridSortOption.SCORE_DESC -> "ranked"
    KitsugiGridSortOption.POPULARITY_DESC -> "popularity"
    KitsugiGridSortOption.YEAR_DESC -> "aired_on"
    KitsugiGridSortOption.TITLE_ASC -> "name"
    KitsugiGridSortOption.SCORE_ASC,
    KitsugiGridSortOption.YEAR_ASC,
    KitsugiGridSortOption.TITLE_DESC,
    KitsugiGridSortOption.DEFAULT -> "popularity"
}

private fun simklSort(sort: KitsugiGridSortOption): String = when (sort) {
    KitsugiGridSortOption.POPULARITY_DESC -> "popular-this-week"
    KitsugiGridSortOption.YEAR_DESC -> "release-date"
    KitsugiGridSortOption.SCORE_DESC -> "rank"
    KitsugiGridSortOption.SCORE_ASC,
    KitsugiGridSortOption.YEAR_ASC,
    KitsugiGridSortOption.TITLE_ASC,
    KitsugiGridSortOption.TITLE_DESC,
    KitsugiGridSortOption.DEFAULT -> "rank"
}

/** Bangumi arama sıralamaları: match | heat | rank | score (bkz. BangumiApiClient.SearchSort). */
private fun bangumiSort(sort: KitsugiGridSortOption): String = when (sort) {
    KitsugiGridSortOption.SCORE_DESC -> "score"
    KitsugiGridSortOption.POPULARITY_DESC -> "heat"
    else -> "rank"
}

// ─────────────────────────────────────────────────────────────────────────────
// Raf bağlamı (kategori → kaynak kısıtları)
// ─────────────────────────────────────────────────────────────────────────────

private data class ExploreShelfSpec(
    val mediaType: MediaType,
    /** null → "tüm formatlar" geçilir (AniList isteği boşa düşmesin). */
    val aniListFormats: List<String>? = null,
    val aniListStatuses: List<String>? = null,
    val aniListCountries: List<String>? = null,
    /** Bir raf birden fazla MAL formatını birleştirebilir (roman → novel + lightnovel). */
    val malFormats: List<String>? = null,
    val malStatus: String? = null,
    val kitsuSubtypes: List<String>? = null,
    val kitsuStatuses: List<String>? = null,
    val shikimoriKinds: List<String>? = null,
    val shikimoriStatuses: List<String>? = null,
    /** true = film, false = dizi. */
    val tmdbKinds: List<Boolean> = emptyList(),
    val tmdbAnimeFocused: Boolean = false,
    val simklType: String = "anime",
    val simklSubtype: String? = null
)

/** AniList/Kitsu/Bangumi gibi kaynaklarda karşılığı olmayan, temel sayfalamada da boş dönen raflar. */
private val unsupportedMangaShelves = setOf(
    ExploreCategoryType.BANGUMI_TV,
    ExploreCategoryType.BANGUMI_MOVIES,
    ExploreCategoryType.UPCOMING_MEDIA_TMDB
)

private fun shelfSpec(platform: ExplorePlatform, categoryType: ExploreCategoryType): ExploreShelfSpec? =
    when (platform) {
        // AniList classifies manhwa/manhua through countryOfOrigin (KR/CN), not a separate format.
        // Bangumi REAL and TMDB "upcoming" shelves have no AniList equivalent.
        ExplorePlatform.AniList -> if (categoryType == ExploreCategoryType.BANGUMI_TV ||
            categoryType == ExploreCategoryType.BANGUMI_MOVIES ||
            categoryType == ExploreCategoryType.UPCOMING_MEDIA_TMDB
        ) null else ExploreShelfSpec(
            mediaType = printMediaType(categoryType),
            aniListFormats = when (categoryType) {
                ExploreCategoryType.MOVIE_ANIME -> listOf("MOVIE")
                ExploreCategoryType.MANHWA_MANHUA -> listOf("MANGA")
                ExploreCategoryType.NOVELS -> listOf("NOVEL")
                else -> null
            },
            aniListStatuses = when (categoryType) {
                ExploreCategoryType.AIRING_ANIME -> listOf("RELEASING")
                ExploreCategoryType.UPCOMING_ANIME -> listOf("NOT_YET_RELEASED")
                else -> null
            },
            aniListCountries = when (categoryType) {
                ExploreCategoryType.MANHWA_MANHUA -> listOf("KR", "CN")
                else -> null
            }
        )
        ExplorePlatform.MAL -> if (categoryType in unsupportedMangaShelves) null
        else ExploreShelfSpec(
            mediaType = printMediaType(categoryType),
            malFormats = when (categoryType) {
                ExploreCategoryType.MOVIE_ANIME -> listOf("movie")
                ExploreCategoryType.MANHWA_MANHUA -> listOf("manhwa", "manhua")
                ExploreCategoryType.NOVELS -> listOf("novel", "lightnovel")
                else -> null
            },
            malStatus = when (categoryType) {
                ExploreCategoryType.AIRING_ANIME -> "airing"
                ExploreCategoryType.UPCOMING_ANIME -> "upcoming"
                ExploreCategoryType.PUBLISHING_MANGA -> "publishing"
                else -> null
            }
        )
        // Kitsu manga subtype filtresi manhwa/manhua ve novel raflarını destekler.
        ExplorePlatform.KITSU -> if (
            categoryType == ExploreCategoryType.BANGUMI_TV ||
            categoryType == ExploreCategoryType.BANGUMI_MOVIES ||
            categoryType == ExploreCategoryType.UPCOMING_MEDIA_TMDB
        ) null else ExploreShelfSpec(
            mediaType = printMediaType(categoryType),
            kitsuSubtypes = when (categoryType) {
                ExploreCategoryType.MOVIE_ANIME -> listOf("movie")
                ExploreCategoryType.MANHWA_MANHUA -> listOf("manhwa", "manhua")
                ExploreCategoryType.NOVELS -> listOf("novel")
                else -> null
            },
            kitsuStatuses = when (categoryType) {
                ExploreCategoryType.AIRING_ANIME -> listOf("current")
                ExploreCategoryType.UPCOMING_ANIME -> listOf("upcoming")
                ExploreCategoryType.PUBLISHING_MANGA -> listOf("current")
                else -> null
            }
        )
        ExplorePlatform.SHIKIMORI -> if (categoryType == ExploreCategoryType.BANGUMI_TV ||
            categoryType == ExploreCategoryType.BANGUMI_MOVIES ||
            categoryType == ExploreCategoryType.UPCOMING_MEDIA_TMDB
        ) null else ExploreShelfSpec(
            mediaType = printMediaType(categoryType),
            shikimoriKinds = when (categoryType) {
                ExploreCategoryType.MOVIE_ANIME -> listOf("movie")
                ExploreCategoryType.MANHWA_MANHUA -> listOf("manhwa", "manhua")
                ExploreCategoryType.NOVELS -> listOf("light_novel", "novel")
                else -> null
            },
            shikimoriStatuses = when (categoryType) {
                ExploreCategoryType.AIRING_ANIME -> listOf("ongoing")
                ExploreCategoryType.UPCOMING_ANIME -> listOf("anons")
                ExploreCategoryType.PUBLISHING_MANGA -> listOf("ongoing")
                else -> null
            }
        )
        ExplorePlatform.BANGUMI -> if (categoryType == ExploreCategoryType.MANHWA_MANHUA ||
            categoryType == ExploreCategoryType.UPCOMING_MEDIA_TMDB
        ) null else ExploreShelfSpec(
            mediaType = when (categoryType) {
                ExploreCategoryType.BANGUMI_TV -> MediaType.TvShow
                ExploreCategoryType.BANGUMI_MOVIES -> MediaType.Movie
                else -> printMediaType(categoryType)
            }
        )
        ExplorePlatform.TMDB -> {
            val (kinds, animeFocused) = when (categoryType) {
                // "Trend Her Şey" / "Trend Medya" / "Yakında Yayında" hem film hem dizi döndürür.
                ExploreCategoryType.TOP_ANIME,
                ExploreCategoryType.TRENDING_ANIME,
                ExploreCategoryType.UPCOMING_MEDIA_TMDB -> listOf(true, false) to false
                ExploreCategoryType.AIRING_ANIME,
                ExploreCategoryType.TOP_MANGA -> listOf(false) to false
                ExploreCategoryType.MOVIE_ANIME,
                ExploreCategoryType.UPCOMING_ANIME,
                ExploreCategoryType.PUBLISHING_MANGA -> listOf(true) to false
                ExploreCategoryType.TRENDING_MANGA,
                ExploreCategoryType.NEWLY_ADDED_ANIME -> listOf(true, false) to true
                ExploreCategoryType.SEASONAL_ANIME -> listOf(false) to false
                // TOP_RATED_ANIME / TOP_RATED_MANGA / NEWLY_ADDED_MANGA için TMDB rafı yok.
                else -> emptyList<Boolean>() to false
            }
            if (kinds.isEmpty()) null
            else ExploreShelfSpec(
                mediaType = MediaType.Movie,
                tmdbKinds = kinds,
                tmdbAnimeFocused = animeFocused
            )
        }
        ExplorePlatform.SIMKL -> ExploreShelfSpec(
            mediaType = when (categoryType) {
                ExploreCategoryType.MOVIE_ANIME,
                ExploreCategoryType.PUBLISHING_MANGA,
                ExploreCategoryType.UPCOMING_ANIME -> MediaType.Movie
                ExploreCategoryType.TOP_ANIME,
                ExploreCategoryType.AIRING_ANIME,
                ExploreCategoryType.TOP_MANGA,
                ExploreCategoryType.SEASONAL_ANIME -> MediaType.TvShow
                else -> MediaType.Anime
            },
            simklType = when (categoryType) {
                ExploreCategoryType.MOVIE_ANIME,
                ExploreCategoryType.PUBLISHING_MANGA,
                ExploreCategoryType.UPCOMING_ANIME -> "movies"
                ExploreCategoryType.TOP_ANIME,
                ExploreCategoryType.AIRING_ANIME,
                ExploreCategoryType.TOP_MANGA,
                ExploreCategoryType.SEASONAL_ANIME -> "tv"
                else -> "anime"
            }
        )
        ExplorePlatform.ALL -> null
    }

/** Yazılı içerik rafları (manga/manhwa/manhua/roman) ile anime raflarını ayırır. */
private fun printMediaType(categoryType: ExploreCategoryType): MediaType = when (categoryType) {
    ExploreCategoryType.TOP_MANGA,
    ExploreCategoryType.TOP_RATED_MANGA,
    ExploreCategoryType.PUBLISHING_MANGA,
    ExploreCategoryType.TRENDING_MANGA,
    ExploreCategoryType.NEWLY_ADDED_MANGA,
    ExploreCategoryType.MANHWA_MANHUA,
    ExploreCategoryType.NOVELS -> MediaType.Manga
    else -> MediaType.Anime
}

/**
 * Mevsimsel rafın kendi sıralama değerini (bkz. `KitsugiSeasonalSort`) genel sıralama
 * seçeneklerine indirger; genel filtre sayfası hiç açılmadıysa kullanıcı seçimi kaybolmaz.
 */
private fun resolveEffectiveSort(
    platform: ExplorePlatform,
    categoryType: ExploreCategoryType,
    sort: KitsugiGridSortOption,
    seasonalSort: String?
): KitsugiGridSortOption {
    if (sort != KitsugiGridSortOption.DEFAULT) return sort
    if (categoryType == ExploreCategoryType.SEASONAL_ANIME && !seasonalSort.isNullOrBlank()) {
        return when (seasonalSort.uppercase()) {
            "SCORE_DESC" -> KitsugiGridSortOption.SCORE_DESC
            "START_DATE_DESC" -> KitsugiGridSortOption.YEAR_DESC
            "MEMBERS_DESC" -> KitsugiGridSortOption.POPULARITY_DESC
            "POPULARITY_DESC" -> KitsugiGridSortOption.POPULARITY_DESC
            else -> ExploreSourceFilters.defaultSort(platform, categoryType)
        }
    }
    return ExploreSourceFilters.defaultSort(platform, categoryType)
}

// ─────────────────────────────────────────────────────────────────────────────
// Sabitler
// ─────────────────────────────────────────────────────────────────────────────

private const val ANILIST_PAGE_SIZE = 24
private const val KITSU_PAGE_SIZE = 20
private const val SHIKIMORI_PAGE_SIZE = 20
private const val SIMKL_PAGE_SIZE = 20
private const val SIMKL_MAX_PAGE = 20
private const val BANGUMI_PAGE_SIZE = 20
private const val TMDB_ANIMATION_GENRE = 16

/**
 * Filtre çipinin kimliği → kaynaklara gönderilen İngilizce kanonik terim.
 * (Kullanıcıya görünen etiket yerelleştirilmiş `stringResource`'tur; API'ye daima İngilizce gider.)
 */
private val GENRE_TERMS: Map<String, String> = KITSUGI_MEDIA_GENRES
    .filter { it.id != "ALL" && it.keywords.isNotEmpty() }
    .associate { it.id to it.keywords.first() }

/**
 * Filtre çiplerinin İngilizce terimlerinin kaynaklarda beklenen kanonik yazımı.
 * AniList GraphQL tür/etiket adları büyük harfle başlar; küçük harfli terim gönderilirse
 * eşleşme boş dönebilir.
 */
private val ENGLISH_TERM_CANONICAL: Map<String, String> = mapOf(
    "drama" to "Drama",
    "action" to "Action",
    "romance" to "Romance",
    "comedy" to "Comedy",
    "fantasy" to "Fantasy",
    "sci-fi" to "Sci-Fi",
    "mystery" to "Mystery",
    "horror" to "Horror",
    "slice of life" to "Slice of Life",
    "adventure" to "Adventure",
    "supernatural" to "Supernatural",
    "psychological" to "Psychological",
    "sports" to "Sports",
    "thriller" to "Thriller",
    "mecha" to "Mecha",
    "music" to "Music",
    "historical" to "Historical",
    "military" to "Military",
    "school" to "School"
)

/** Bangumi arama uç noktasının uygulayabildiği sıralamalar (etiketli aramada). */
private val BANGUMI_SEARCH_SORTS = setOf(
    KitsugiGridSortOption.POPULARITY_DESC,
    KitsugiGridSortOption.SCORE_DESC
)

/**
 * Bangumi (bgm.tv) etiket sözlüğü — keşfet tür çiplerinin yerel karşılıkları.
 * Bangumi etiketleri topluluk tarafından girilir ve ağırlıklı olarak yerel dilde tutulur;
 * eşlenemeyen bir çip sunucuya gönderilmez, cihazda süzülür.
 */
/**
 * Arayüz tür terimini Bangumi'nin **kendi** etiket yazımına çevirir.
 *
 * Terim çevirisi tek yerde (`BangumiTagDictionary`) yapılır: sözlük 58 bin etiket
 * grubunu kapsar ve her grup için sunucuda dolu olan yazımı seçer
 * (`运动系` değil `运动`). Burada ayrıca 19 girişlik ikinci bir harita tutmak,
 * iki kaynağın ayrışmasına (örn. `Supernatural` → `超能力` vs `灵异`) yol açıyordu.
 */
private fun bangumiTagFor(term: String): String? =
    com.kitsugi.animelist.utils.BangumiTagDictionary.canonicalTagFor(term)

/**
 * Kaynak API'lerinin gerçekten uygulayabildiği sıralama seçenekleri.
 * Listede olmayan bir sıralama seçilirse sayfa eski davranışı korur (cihazda sıralar) ve
 * filtre sayfası bu çipi "📱 yerel" olarak işaretler.
 */
private val SORT_SUPPORT: Map<ExplorePlatform, Set<KitsugiGridSortOption>> = mapOf(
    ExplorePlatform.AniList to KitsugiGridSortOption.entries.toSet(),
    ExplorePlatform.MAL to setOf(
        KitsugiGridSortOption.SCORE_DESC,
        KitsugiGridSortOption.POPULARITY_DESC,
        KitsugiGridSortOption.YEAR_DESC,
        KitsugiGridSortOption.YEAR_ASC,
        KitsugiGridSortOption.TITLE_ASC,
        KitsugiGridSortOption.TITLE_DESC
    ),
    ExplorePlatform.TMDB to setOf(
        KitsugiGridSortOption.SCORE_DESC,
        KitsugiGridSortOption.SCORE_ASC,
        KitsugiGridSortOption.POPULARITY_DESC,
        KitsugiGridSortOption.YEAR_DESC,
        KitsugiGridSortOption.YEAR_ASC
    ),
    ExplorePlatform.KITSU to KitsugiGridSortOption.entries.toSet(),
    ExplorePlatform.SHIKIMORI to setOf(
        KitsugiGridSortOption.SCORE_DESC,
        KitsugiGridSortOption.POPULARITY_DESC,
        KitsugiGridSortOption.YEAR_DESC,
        KitsugiGridSortOption.TITLE_ASC
    ),
    // Simkl tür uç noktasında puan/başlık sıralaması yok (rank, popülerlik, yayın tarihi var).
    ExplorePlatform.SIMKL to setOf(
        KitsugiGridSortOption.POPULARITY_DESC,
        KitsugiGridSortOption.YEAR_DESC
    ),
    // Bangumi türle birlikte yalnız sıcaklık/puan/rank sıralarını destekler; varsayılan sıra yeterli.
    ExplorePlatform.BANGUMI to emptySet(),
    ExplorePlatform.ALL to setOf(
        KitsugiGridSortOption.SCORE_DESC,
        KitsugiGridSortOption.POPULARITY_DESC,
        KitsugiGridSortOption.YEAR_DESC
    )
)
