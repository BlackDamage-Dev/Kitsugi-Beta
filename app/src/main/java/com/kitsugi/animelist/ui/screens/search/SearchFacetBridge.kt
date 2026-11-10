package com.kitsugi.animelist.ui.screens.search

import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.utils.bangumiNativeTagOrNull
import java.util.Locale

/**
 * "Tümü" (7 kaynak) aramasında filtre ↔ kaynak uyumluluğunun TEK karar noktası.
 *
 * Geçmişte global tür/etiket/yıl facet'leri yalnızca AniList rafına uygulanıyordu;
 * diğer 6 raf aynı filtreler varken **filtresiz popüler** sonuç döndürüyordu (ekrandaki
 * alakasız MAL/TMDB kartları). Ayrıca detay sayfasından gelen serbest metin facet'leri
 * doğrulanmadan AniList `tag_in`, Kitsu `filter[categories]` ve Bangumi `filter.tag`
 * alanlarına yazılıyor, tanınmayan değerler HTTP 400 → `KTS-COM-1016` üretiyordu.
 *
 * Kural: bir kaynak seçili facet'i **gerçekten uygulayamıyorsa hiç sorgulanmaz**.
 * Böylece ne filtresiz sonuç sızar ne de geçersiz parametre gönderilir.
 */
internal data class SearchFacetSnapshot(
    /** Kanonik İngilizce tür/tema/demografi/etiket terimleri. */
    val terms: List<String> = emptyList(),
    val excludedTerms: List<String> = emptyList(),
    val startYear: Int? = null,
    val endYear: Int? = null,
    val season: String? = null,
    val mediaType: MediaType = MediaType.Anime,
    /** Jikan/MAL producer kimliği (AniList studio ID'si ile AYNI ad alanı değildir). */
    val producerId: Int? = null,
    val magazineId: Int? = null,
    /** TMDB network + keyword kimlikleri. */
    val networkId: Int? = null,
    val tmdbKeywordId: Int? = null,
    val hasQuery: Boolean = false,
    val detailRequest: DetailSearchFilterRequest? = null,
    /** Kaynağa özel filtre demetlerinin aktif filtre sayıları. */
    val engineSpecificActive: Map<SearchSourceEngine, Int> = emptyMap()
) {
    /** Terim/kimlik tabanlı (sıralama dışındaki) facet var mı? */
    val hasTermFacets: Boolean
        get() = terms.isNotEmpty() || excludedTerms.isNotEmpty() || startYear != null ||
            endYear != null || season != null || producerId != null || magazineId != null ||
            networkId != null || tmdbKeywordId != null

    val hasFacets: Boolean
        get() = hasTermFacets || engineSpecificActive.values.any { it > 0 }

    val isManga: Boolean get() = mediaType == MediaType.Manga
    val isTv: Boolean get() = mediaType == MediaType.TvShow

    /** Kapsamın desteklediği ama mevcut filtre/facet yüzünden atlanan kaynaklar. */
    fun skippedEngines(base: AllSourcePlan): List<SearchSourceEngine> {
        val restricted = SearchFacetBridge.restrict(base, this)
        return buildList {
            if (base.aniList && !restricted.aniList) add(SearchSourceEngine.ANILIST)
            if (base.mal && !restricted.mal) add(SearchSourceEngine.MAL)
            if (base.tmdb && !restricted.tmdb) add(SearchSourceEngine.TMDB)
            if (base.shikimori && !restricted.shikimori) add(SearchSourceEngine.SHIKIMORI)
            if (base.kitsu && !restricted.kitsu) add(SearchSourceEngine.KITSU)
            if (base.simkl && !restricted.simkl) add(SearchSourceEngine.SIMKL)
            if (base.bangumi && !restricted.bangumi) add(SearchSourceEngine.BANGUMI)
        }
    }
}

internal object SearchFacetBridge {

    fun supportsEngine(engine: SearchSourceEngine, facets: SearchFacetSnapshot): Boolean {
        val detailOk = facets.detailRequest?.let {
            CanonicalFilterBridge.supportsDetailFilter(engine, it)
        } ?: true
        return detailOk && supportsTerms(engine, facets)
    }

    /**
     * Kitsu `filter[categories]` yalnızca **slug** kabul eder. "Action", "Isekai" gibi
     * görünen adları doğrudan göndermek 400 döndürüyordu; yalnızca bilinen eşlemeler
     * gönderilir, bilinmeyen terimde Kitsu rafı kapatılır.
     */
    private val kitsuCategorySlugs: Map<String, String> = mapOf(
        "action" to "action", "adventure" to "adventure", "comedy" to "comedy",
        "drama" to "drama", "ecchi" to "ecchi", "fantasy" to "fantasy",
        "horror" to "horror", "mahou shoujo" to "mahou-shoujo", "mecha" to "mecha",
        "music" to "music", "mystery" to "mystery", "psychological" to "psychological",
        "romance" to "romance", "sci-fi" to "sci-fi", "science fiction" to "sci-fi",
        "bilim kurgu" to "sci-fi", "slice of life" to "slice-of-life",
        "sports" to "sports", "supernatural" to "supernatural", "thriller" to "thriller",
        "shounen" to "shounen", "shoujo" to "shoujo", "seinen" to "seinen", "josei" to "josei",
        "kids" to "kids", "harem" to "harem", "reverse harem" to "reverse-harem",
        "isekai" to "isekai", "magic" to "magic", "military" to "military",
        "school" to "school", "vampire" to "vampire", "samurai" to "samurai",
        "martial arts" to "martial-arts", "historical" to "historical", "space" to "space",
        "super power" to "super-power", "survival" to "survival", "time travel" to "time-travel",
        "video games" to "video-games", "reincarnation" to "reincarnation",
        "yuri" to "yuri", "boys' love" to "yaoi", "gore" to "gore", "cyberpunk" to "cyberpunk",
        "post-apocalyptic" to "post-apocalyptic", "youkai" to "youkai", "wuxia" to "wuxia",
        "cultivation" to "cultivation", "mafia" to "mafia", "yakuza" to "yakuza",
        "police" to "police", "gourmet" to "gourmet", "idols" to "idols", "idol" to "idols"
    )

    fun mapKitsuCategory(term: String): String? =
        kitsuCategorySlugs[SearchTranslation.translateToEnglishForSearch(term).trim().lowercase(Locale.ROOT)]

    /** Bangumi `filter.tag` özgün wiki etiketi bekler; İngilizce ad köprü sözlüğünden çevrilir. */
    fun mapBangumiTag(term: String): String? = bangumiNativeTagOrNull(term)

    // ─────────────────────────────────────────────────────────────────────────
    // Durum → kanonik facet anlık görüntüsü
    // ─────────────────────────────────────────────────────────────────────────

    fun fromState(state: SearchUiState, scope: SearchScope, query: String): SearchFacetSnapshot {
        val globalTerms = (state.genres + state.tags)
            .mapNotNull { term -> SearchTaxonomy.canonical(term) }
            .distinct()
        val globalExcluded = state.excludedGenres
            .mapNotNull { term -> SearchTaxonomy.canonical(term) }
            .distinct()

        // Detay facet'i varken de filtre sayfasından eklenen global tür/etiketler
        // hesaba katılır. Aksi halde iki filtre katmanı birbirini görünmez şekilde
        // eziyor, "2 filtre" rozetine rağmen yalnızca biri uygulanıyordu.
        val request = state.detailSearchFilterRequest
        if (request != null) {
            val detail = forDetailRequest(request, hasQuery = query.isNotBlank())
            return detail.copy(
                terms = (detail.terms + globalTerms).distinct(),
                excludedTerms = globalExcluded,
                startYear = detail.startYear ?: state.startYear,
                endYear = detail.endYear ?: state.endYear,
                season = detail.season ?: state.season?.apiValue,
                engineSpecificActive = engineFacets(state)
            )
        }

        val terms = globalTerms
        val excluded = globalExcluded
        val mediaType = when {
            scope.isPrintScope -> MediaType.Manga
            scope == SearchScope.MOVIE -> MediaType.Movie
            scope == SearchScope.TV || scope == SearchScope.K_DRAMA -> MediaType.TvShow
            else -> state.selectedMediaType
        }
        return SearchFacetSnapshot(
            terms = terms,
            excludedTerms = excluded,
            startYear = state.startYear,
            endYear = state.endYear,
            season = state.season?.apiValue,
            mediaType = mediaType,
            hasQuery = query.isNotBlank(),
            engineSpecificActive = engineFacets(state)
        )
    }

    /** Detay sayfası çip tıklamaları için tek facet modeli. */
    fun forDetailRequest(request: DetailSearchFilterRequest, hasQuery: Boolean = false): SearchFacetSnapshot {
        val facetTerms = listOfNotNull(request.genre, request.theme, request.demographic)
            .mapNotNull { term -> SearchTaxonomy.canonical(term) }
        val tag = request.tag?.let { term -> SearchTaxonomy.canonical(term) }
        val season = CanonicalFilterBridge.normalizeSeason(request.season)
        // ID ad alanı koruması: AniList studio ID'si Jikan producer ID'si DEĞİLDİR.
        // Yalnızca Jikan/MAL kaynaklı producer id'si Jikan `producers` parametresine gider.
        val jikanProducer = request.producerId?.takeIf { it > 0 }?.takeIf {
            request.source.equals("mal", ignoreCase = true) || request.source.equals("jikan", ignoreCase = true)
        }
        val tmdbKeyword = request.keywordId?.takeIf { it > 0 }
            ?.takeIf { CanonicalFilterBridge.isTmdbTagSource(request.tagSource) }
        return SearchFacetSnapshot(
            terms = (facetTerms + listOfNotNull(tag)).distinct(),
            startYear = request.year?.takeIf { it > 0 },
            endYear = request.year?.takeIf { it > 0 },
            season = season,
            mediaType = request.mediaType,
            producerId = jikanProducer,
            magazineId = request.magazineId?.takeIf { it > 0 },
            networkId = request.networkId?.takeIf { it > 0 },
            tmdbKeywordId = tmdbKeyword,
            hasQuery = hasQuery,
            detailRequest = request
        )
    }

    private fun engineFacets(state: SearchUiState): Map<SearchSourceEngine, Int> = mapOf(
        SearchSourceEngine.ANILIST to state.aniListSpecificFilters.activeCount,
        SearchSourceEngine.MAL to state.malSpecificFilters.activeCount,
        SearchSourceEngine.TMDB to state.tmdbSpecificFilters.activeCount,
        SearchSourceEngine.SHIKIMORI to state.shikimoriSpecificFilters.activeCount,
        SearchSourceEngine.KITSU to state.kitsuSpecificFilters.activeCount,
        SearchSourceEngine.SIMKL to state.simklSpecificFilters.activeCount,
        SearchSourceEngine.BANGUMI to state.bangumiSpecificFilters.activeCount
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Kaynak planı
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Kapsam planını facet planıyla KESİŞTİRİR. Filtreyi uygulayamayan kaynak kapatılır;
     * aksi halde o raf filtresiz popüler sonuçlarla dolar ya da 400 hatası üretir.
     */
    fun restrict(base: AllSourcePlan, facets: SearchFacetSnapshot): AllSourcePlan {
        val active = facets.engineSpecificActive
        val anySpecificActive = active.values.any { it > 0 }
        // Global/detail facet'ler zaten HER rafa ayrı ayrı çevrilir. Kaynağa özel
        // demet yalnızca kendi rafına uygulanır; global facet varken diğer rafları
        // kapatmak gereksiz yere 6 rafın kaybolmasına yol açardı.
        val hasGlobalFacets = facets.hasTermFacets || facets.detailRequest != null

        fun allow(engine: SearchSourceEngine, enabled: Boolean): Boolean {
            if (!enabled) return false
            val detailOk = facets.detailRequest?.let {
                CanonicalFilterBridge.supportsDetailFilter(engine, it)
            } ?: true
            if (!detailOk) return false
            if (!supportsTerms(engine, facets)) return false
            // Yalnızca kaynağa özel filtre seçildiyse, o filtreyi taşımayan raflar
            // filtresiz popüler sonuç üretmesin diye kapatılır.
            if (anySpecificActive && !hasGlobalFacets && (active[engine] ?: 0) == 0) return false
            return true
        }

        return base.copy(
            aniList = allow(SearchSourceEngine.ANILIST, base.aniList),
            mal = allow(SearchSourceEngine.MAL, base.mal),
            tmdb = allow(SearchSourceEngine.TMDB, base.tmdb),
            shikimori = allow(SearchSourceEngine.SHIKIMORI, base.shikimori),
            kitsu = allow(SearchSourceEngine.KITSU, base.kitsu),
            simkl = allow(SearchSourceEngine.SIMKL, base.simkl),
            bangumi = allow(SearchSourceEngine.BANGUMI, base.bangumi)
        )
    }

    /** Bu kaynak seçili terimleri kendi API'sinde temsil edebiliyor mu? */
    fun supportsTerms(engine: SearchSourceEngine, facets: SearchFacetSnapshot): Boolean {
        // ID ad alanları: bu kimliği gerçekten kullanabilen kaynak dışındakiler
        // kapatılır, ama o kaynak için terim doğrulaması yine de yapılır.
        if (facets.producerId != null || facets.magazineId != null) {
            if (engine != SearchSourceEngine.MAL) return false
        }
        if (facets.networkId != null || facets.tmdbKeywordId != null) {
            if (engine != SearchSourceEngine.TMDB) return false
        }

        // Tek terim doğrulayıcı: hem dahil EDİLEN hem HARİÇ TUTULAN terimler aynı
        // sözlükten geçer. Eskiden hariç tutulan terimler yalnızca AniList için
        // doğrulanıyordu; MAL/TMDB/Shikimori'de tanınmayan bir hariç tutma sessizce
        // yok sayılıp kullanıcının dışlamak istediği içerik rafta görünüyordu.
        val canRepresent: (String) -> Boolean = when (engine) {
            SearchSourceEngine.ANILIST -> { term ->
                // Resmî türler `genre_in`/`genre_not_in`, etiketler `tag_in`/`tag_not_in`.
                SearchTaxonomy.isOfficialApiGenre(term) || SearchTaxonomy.isKnownAniListTag(term)
            }
            SearchSourceEngine.MAL -> { term ->
                CanonicalFilterBridge.mapMalGenreId(term, facets.isManga) != null
            }
            SearchSourceEngine.TMDB -> { term ->
                CanonicalFilterBridge.mapTmdbGenreId(term, facets.isTv) != null ||
                    (facets.tmdbKeywordId != null &&
                        facets.detailRequest?.tag?.let(SearchTaxonomy::canonical)?.equals(term, ignoreCase = true) == true)
            }
            SearchSourceEngine.SHIKIMORI -> { term ->
                CanonicalFilterBridge.mapAnimeGenreId(term) != null ||
                    CanonicalFilterBridge.mapMalGenreId(term, facets.isManga) != null
            }
            SearchSourceEngine.KITSU -> { term -> mapKitsuCategory(term) != null }
            SearchSourceEngine.SIMKL -> { term ->
                CanonicalFilterBridge.mapSimklGenre(term) != null
            }
            SearchSourceEngine.BANGUMI -> { term -> mapBangumiTag(term) != null }
            SearchSourceEngine.ALL -> { _ -> true }
        }
        if (!facets.terms.all(canRepresent)) return false
        if (!facets.excludedTerms.all(canRepresent)) return false
        // Hariç tutmayı API'sinde ifade edemeyen kaynaklar kapatılır; aksi halde
        // dışlanan içerik filtresizmiş gibi rafta görünür.
        if (facets.excludedTerms.isNotEmpty() && engine !in setOf(
                SearchSourceEngine.ANILIST, SearchSourceEngine.MAL,
                SearchSourceEngine.TMDB, SearchSourceEngine.SHIKIMORI
            )
        ) return false

        // Mevsim, yıldan daha katı bir filtredir; yalnızca yıl destekleyen kaynak
        // tüm yılın kataloğunu döndürmesin diye kapatılır.
        if (facets.season != null && engine !in setOf(
                SearchSourceEngine.ANILIST, SearchSourceEngine.MAL,
                SearchSourceEngine.SHIKIMORI, SearchSourceEngine.KITSU
            )
        ) return false
        // Metin sorgusu + facet: TMDB/Simkl iki ucu birleştiremediği için filtresiz
        // aramaya düşmek yerine kapatılır.
        if (facets.hasQuery && facets.hasFacets &&
            engine in setOf(SearchSourceEngine.TMDB, SearchSourceEngine.SIMKL)
        ) return false
        return true
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Kaynaklara gönderilecek çevrilmiş değerler (yalnızca doğrulanmış terimler)
    // ─────────────────────────────────────────────────────────────────────────

    fun aniListGenres(facets: SearchFacetSnapshot): List<String> =
        facets.terms.filter(SearchTaxonomy::isOfficialApiGenre)

    fun aniListTags(facets: SearchFacetSnapshot): List<String> =
        facets.terms.filterNot(SearchTaxonomy::isOfficialApiGenre)
            .filter(SearchTaxonomy::isKnownAniListTag)

    fun aniListExcludedGenres(facets: SearchFacetSnapshot): List<String> =
        facets.excludedTerms.filter(SearchTaxonomy::isOfficialApiGenre)

    /** Demografi/tema gibi resmî olmayan türler AniList'te `tag_not_in` ile dışlanır. */
    fun aniListExcludedTags(facets: SearchFacetSnapshot): List<String> =
        facets.excludedTerms.filterNot(SearchTaxonomy::isOfficialApiGenre)
            .filter(SearchTaxonomy::isKnownAniListTag)

    fun malGenreIds(facets: SearchFacetSnapshot): List<Int> =
        facets.terms.mapNotNull { CanonicalFilterBridge.mapMalGenreId(it, facets.isManga)?.toIntOrNull() }

    fun malExcludedGenreIds(facets: SearchFacetSnapshot): List<Int> =
        facets.excludedTerms.mapNotNull { CanonicalFilterBridge.mapMalGenreId(it, facets.isManga)?.toIntOrNull() }

    fun tmdbGenreIds(facets: SearchFacetSnapshot): List<Int> =
        facets.terms.mapNotNull { CanonicalFilterBridge.mapTmdbGenreId(it, facets.isTv) }

    fun tmdbExcludedGenreIds(facets: SearchFacetSnapshot): List<Int> =
        facets.excludedTerms.mapNotNull { CanonicalFilterBridge.mapTmdbGenreId(it, facets.isTv) }

    fun shikimoriGenreIds(facets: SearchFacetSnapshot): List<Int> =
        facets.terms.mapNotNull {
            CanonicalFilterBridge.mapAnimeGenreId(it)
                ?: CanonicalFilterBridge.mapMalGenreId(it, facets.isManga)?.toIntOrNull()
        }

    fun shikimoriExcludedGenreIds(facets: SearchFacetSnapshot): List<Int> =
        facets.excludedTerms.mapNotNull {
            CanonicalFilterBridge.mapAnimeGenreId(it)
                ?: CanonicalFilterBridge.mapMalGenreId(it, facets.isManga)?.toIntOrNull()
        }

    fun kitsuCategories(facets: SearchFacetSnapshot): List<String> =
        facets.terms.mapNotNull(::mapKitsuCategory)

    fun simklGenre(facets: SearchFacetSnapshot): String? =
        facets.terms.mapNotNull { CanonicalFilterBridge.mapSimklGenre(it) }.firstOrNull()

    fun bangumiTags(facets: SearchFacetSnapshot): List<String> =
        facets.terms.mapNotNull(::mapBangumiTag)

    fun malSeason(facets: SearchFacetSnapshot): String? = facets.season?.lowercase(Locale.ROOT)

    fun shikimoriSeason(facets: SearchFacetSnapshot): String? = when {
        facets.season != null && facets.startYear != null -> "${facets.season.lowercase(Locale.ROOT)}_${facets.startYear}"
        facets.startYear != null -> facets.startYear.toString()
        else -> null
    }

    fun kitsuSeason(facets: SearchFacetSnapshot): String? = facets.season?.lowercase(Locale.ROOT)

    fun simklYear(facets: SearchFacetSnapshot): String? =
        (facets.endYear ?: facets.startYear)?.toString()

    fun tmdbKeyword(facets: SearchFacetSnapshot): String? = facets.tmdbKeywordId?.toString()
}
