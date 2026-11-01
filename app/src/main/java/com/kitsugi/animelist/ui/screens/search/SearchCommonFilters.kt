package com.kitsugi.animelist.ui.screens.search

import java.util.Locale
import kotlin.math.roundToInt

/**
 * ORTAK FİLTRE ÇATISI (Common Filter Roof)
 * ─────────────────────────────────────────────────────────────────────────────
 * Arama sayfasındaki "Tümü" motoru ile her kaynağın "Tümünü Gör" sayfasındaki
 * filtre sheet'i TEK bir kanonik model üzerinden çalışır.
 *
 * Neden gerekliydi?
 *  - "Tümü" modunda seçilen tür/etiket/kurum/yıl/puan/sıralama yalnızca AniList
 *    rafına uygulanıyordu; MAL, TMDB, Shikimori, Kitsu, Simkl ve Bangumi rafları
 *    kendi boş paketleriyle sorgulanıyordu.
 *  - "Tümünü Gör" ile kaynağa özel sayfaya geçildiğinde yeni ViewModel örneği
 *    oluştuğu için seçimler tamamen kayboluyordu (bkz. kullanıcı raporu).
 *
 * Model iki yönlüdür:
 *  1. [harvest] : mevcut [SearchUiState] → kanonik [CommonSearchFilters]
 *  2. [applyTo] : kanonik [CommonSearchFilters] → hedef motorun kendi paketi
 *
 * Böylece hangi ekranda seçim yapılırsa yapılsın diğer sayfalara AYNI anlamla taşınır.
 */
data class CommonSearchFilters(
    /** İngilizce kanonik tür adları (AniList/Jikan/TMDB adlandırması ile uyumlu). */
    val genres: List<String> = emptyList(),
    /** Hariç tutulan tür adları. */
    val excludedGenres: List<String> = emptyList(),
    /** Etiketler (AniList tag / Kitsu category / Bangumi tag). */
    val tags: List<String> = emptyList(),
    /** Kurumlar (stüdyo / yapımcı / yayıncı / kanal). Kaynağa bağlıdır. */
    val institutions: List<CommonInstitution> = emptyList(),
    val startYear: Int? = null,
    val endYear: Int? = null,
    val minScore: Int? = null,
    val maxScore: Int? = null,
    /** AniList sıralama anahtarı (POPULARITY_DESC, SCORE_DESC ...) kanonik biçimdir. */
    val sortKey: String? = null
) {
    val genreTagCount: Int get() = genres.size + excludedGenres.size + tags.size

}

/**
 * Kaynağa bağlı kurum (stüdyo/yapımcı/şirket/kanal) seçimi.
 * [engine] alanı kurum kimliğinin hangi kaynağın kimlik uzayına ait olduğunu tutar:
 * MAL yapımcı ID'si ile TMDB şirket ID'si aynı sayı olsa bile farklı kurumdur.
 */
data class CommonInstitution(
    val engine: SearchSourceEngine,
    val id: Int,
    val name: String,
    val kind: CommonInstitutionKind = CommonInstitutionKind.PRODUCER
) {
    val key: String get() = "${engine.id}:${kind.name}:$id"
}

/** Kurum türleri. Her motor yalnızca desteklediği türleri listeler. */
enum class CommonInstitutionKind {
    STUDIO,
    PRODUCER,
    COMPANY,
    NETWORK,
    PUBLISHER,
    MAGAZINE
}

/**
 * Kanonik model ile motor paketleri arasındaki iki yönlü köprü.
 * Tüm fonksiyonlar saf (pure) tutuldu; bu sayede kolay test edilebilir.
 */
object SearchCommonFiltersBridge {

    /** AniList sıralama anahtarları (kanonik küme). */
    private val aniListSortKeys = setOf(
        "SEARCH_MATCH", "POPULARITY_DESC", "SCORE_DESC", "TRENDING_DESC",
        "FAVOURITES_DESC", "START_DATE_DESC", "START_DATE_ASC",
        "TITLE_ROMAJI", "TITLE_ROMAJI_DESC", "TITLE_ENGLISH", "TITLE_NATIVE"
    )

    /** Kurum filtresi sunucu tarafında uygulanabilen motorlar. */
    fun supportsInstitutions(engine: SearchSourceEngine): Boolean = when (engine) {
        SearchSourceEngine.ALL, SearchSourceEngine.MAL, SearchSourceEngine.TMDB -> true
        else -> false
    }

    /** Seçilen motorun kurum sekmesinde listelenecek türler. */
    fun institutionKindsFor(engine: SearchSourceEngine): List<CommonInstitutionKind> = when (engine) {
        SearchSourceEngine.MAL -> listOf(CommonInstitutionKind.PRODUCER)
        SearchSourceEngine.TMDB -> listOf(CommonInstitutionKind.COMPANY)
        SearchSourceEngine.ALL -> listOf(
            CommonInstitutionKind.PRODUCER, CommonInstitutionKind.COMPANY
        )
        else -> emptyList()
    }

    /**
     * Kurum araması yapılabilen tür mü? (UI'da arama alanı gösterilir.)
     *
     * Yalnızca arkasında gerçek bir arama ucu olan türler açılır: MAL yapımcı
     * (`/producers`) ve TMDB şirket (`/search/company`). Kanal/dergi türleri
     * şu an ayrı bir kimlik araması gerektirdiği için kapalıdır; yanlış kimlik
     * yazıp filtreyi bozmaktansa desteklenmediğini söylemek doğrudur.
     */
    fun canSearchInstitutionKind(kind: CommonInstitutionKind): Boolean = when (kind) {
        CommonInstitutionKind.PRODUCER, CommonInstitutionKind.COMPANY -> true
        else -> false
    }

    /** Kurum türü seçildiğinde hangi motora yazılacağı. */
    fun engineForInstitutionKind(kind: CommonInstitutionKind): SearchSourceEngine = when (kind) {
        CommonInstitutionKind.PRODUCER, CommonInstitutionKind.MAGAZINE -> SearchSourceEngine.MAL
        CommonInstitutionKind.COMPANY, CommonInstitutionKind.NETWORK -> SearchSourceEngine.TMDB
        CommonInstitutionKind.STUDIO, CommonInstitutionKind.PUBLISHER -> SearchSourceEngine.SHIKIMORI
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1) DURUM → KANONİK
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Mevcut arama durumundan kanonik filtre setini üretir.
     *
     * Hem kök alanlar (`genres`, `tags`) hem de seçili motorun kendi paketi
     * birleştirilir; böylece hangi ekranda seçim yapıldıysa kaybolmaz.
     */
    fun harvest(state: SearchUiState): CommonSearchFilters {
        val genres = LinkedHashSet<String>()
        val excluded = LinkedHashSet<String>()
        val tags = LinkedHashSet<String>()

        genres.addAll(state.genres.filter { it.isNotBlank() })
        excluded.addAll(state.excludedGenres.filter { it.isNotBlank() })
        tags.addAll(state.tags.filter { it.isNotBlank() })

        when (state.selectedEngine) {
            SearchSourceEngine.ALL, SearchSourceEngine.ANILIST -> {
                val f = state.aniListSpecificFilters
                genres.addAll(f.genres.filter { it.isNotBlank() })
                excluded.addAll(f.excludedGenres.filter { it.isNotBlank() })
                tags.addAll(f.tags.filter { it.isNotBlank() })
            }
            SearchSourceEngine.MAL -> {
                val f = state.malSpecificFilters
                genres.addAll(f.genres.mapNotNull(::malGenreName))
                excluded.addAll(f.excludedGenres.mapNotNull(::malGenreName))
            }
            SearchSourceEngine.TMDB -> {
                val f = state.tmdbSpecificFilters
                val isTv = state.selectedScope == SearchScope.TV || state.selectedScope == SearchScope.K_DRAMA
                genres.addAll(f.genres.mapNotNull { tmdbGenreName(it, isTv) })
                excluded.addAll(f.excludedGenres.mapNotNull { tmdbGenreName(it, isTv) })
            }
            SearchSourceEngine.SHIKIMORI -> {
                val f = state.shikimoriSpecificFilters
                genres.addAll(f.genres.mapNotNull(::malGenreName))
                excluded.addAll(f.excludedGenres.mapNotNull(::malGenreName))
            }
            SearchSourceEngine.KITSU -> {
                // Kitsu kategorileri slug olarak saklanır ("slice-of-life"). Kanonik
                // sete okunabilir ad yazılmazsa "Tümü"/AniList tarafına slug gidiyor
                // ve hiçbir kayıtla eşleşmiyordu; sheet'te de çirkin bir çip görünüyordu.
                tags.addAll(
                    state.kitsuSpecificFilters.categories
                        .filter { it.isNotBlank() }
                        .map { kitsuSlugToName(it) }
                )
            }
            SearchSourceEngine.SIMKL -> {
                state.simklSpecificFilters.genre?.let { slug ->
                    simklGenreName(slug)?.let(genres::add)
                }
            }
            SearchSourceEngine.BANGUMI -> {
                tags.addAll(state.bangumiSpecificFilters.tags.filter { it.isNotBlank() })
            }
        }

        val startYear = state.startYear ?: engineStartYear(state)
        val endYear = state.endYear ?: engineEndYear(state)
        val minScore = state.minScore ?: engineMinScore(state)
        val maxScore = state.maxScore ?: engineMaxScore(state)
        val sortKey = canonicalSortKey(state.selectedEngine, getCurrentSortKey(state))

        return CommonSearchFilters(
            genres = genres.toList(),
            excludedGenres = excluded.toList(),
            tags = tags.toList(),
            institutions = state.institutions,
            startYear = startYear,
            endYear = endYear,
            minScore = minScore,
            maxScore = maxScore,
            sortKey = sortKey
        )
    }

    private fun engineStartYear(state: SearchUiState): Int? = when (state.selectedEngine) {
        SearchSourceEngine.ALL, SearchSourceEngine.ANILIST -> state.aniListSpecificFilters.startYear
        SearchSourceEngine.MAL -> state.malSpecificFilters.startYear
        SearchSourceEngine.TMDB -> state.tmdbSpecificFilters.startYear
        SearchSourceEngine.SHIKIMORI ->
            state.shikimoriSpecificFilters.season?.toIntOrNull()
        SearchSourceEngine.KITSU -> state.kitsuSpecificFilters.seasonYear
        SearchSourceEngine.SIMKL -> state.simklSpecificFilters.year?.toIntOrNull()
        SearchSourceEngine.BANGUMI -> state.bangumiSpecificFilters.yearFrom
    }

    private fun engineEndYear(state: SearchUiState): Int? = when (state.selectedEngine) {
        SearchSourceEngine.ALL, SearchSourceEngine.ANILIST -> state.aniListSpecificFilters.endYear
        SearchSourceEngine.MAL -> state.malSpecificFilters.endYear
        SearchSourceEngine.TMDB -> state.tmdbSpecificFilters.endYear
        SearchSourceEngine.BANGUMI -> state.bangumiSpecificFilters.yearTo
        else -> null
    }

    private fun engineMinScore(state: SearchUiState): Int? = when (state.selectedEngine) {
        SearchSourceEngine.ALL, SearchSourceEngine.ANILIST -> state.aniListSpecificFilters.minScore
        // 0–10 ölçeğindeki motor değerleri kanonik 0–100'e yükseltilir.
        SearchSourceEngine.MAL -> tenToCanonical(state.malSpecificFilters.minScore)
        SearchSourceEngine.TMDB -> tenToCanonical(state.tmdbSpecificFilters.minScore)
        SearchSourceEngine.SHIKIMORI -> tenToCanonical(state.shikimoriSpecificFilters.minScore)
        // Kitsu `averageRating` 0–100 ölçeğindedir; kanonikle aynı.
        SearchSourceEngine.KITSU -> state.kitsuSpecificFilters.minRating
        // Simkl arama ucu puan filtresi kabul etmiyor (bkz. SimklSpecificFilters).
        SearchSourceEngine.BANGUMI -> tenToCanonical(state.bangumiSpecificFilters.minScore)
        else -> null
    }

    private fun engineMaxScore(state: SearchUiState): Int? = when (state.selectedEngine) {
        SearchSourceEngine.ALL, SearchSourceEngine.ANILIST -> state.aniListSpecificFilters.maxScore
        SearchSourceEngine.MAL -> tenToCanonical(state.malSpecificFilters.maxScore)
        SearchSourceEngine.TMDB -> tenToCanonical(state.tmdbSpecificFilters.maxScore)
        else -> null
    }

    // ── Puan ölçeği çevirileri ──────────────────────────────────────────────
    // Kanonik model 0–100 kullanır (AniList / Kitsu / Simkl ölçeği).
    // MAL (Jikan), TMDB, Shikimori ve Bangumi 0–10 (10'luk) ölçek kullanır.

    /** 0–100 → 0–10 (ondalıklı). 0 ve negatif değerler "filtre yok" sayılır. */
    private fun canonicalScoreToTen(canonical: Int?): Double? =
        canonical?.takeIf { it > 0 }?.div(10.0)

    /** 0–100 → 0–10 (tam sayı; Shikimori / Bangumi). */
    private fun canonicalScoreToTenInt(canonical: Int?): Int? =
        canonical?.takeIf { it > 0 }?.let { (it / 10.0).roundToInt() }?.takeIf { it > 0 }

    /** 0–10 → 0–100. */
    private fun tenToCanonical(source: Double?): Int? =
        source?.takeIf { it > 0 }?.let { (it * 10).roundToInt() }?.takeIf { it > 0 }

    /** 0–10 → 0–100 (tam sayı kaynaklar için). */
    private fun tenToCanonical(source: Int?): Int? =
        source?.takeIf { it > 0 }?.times(10)

    // ─────────────────────────────────────────────────────────────────────────
    // 2) KANONİK → DURUM
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Kanonik filtreleri hedef motora uygular.
     *
     * - Kök alanlar (genres / tags / institutions / yıl / puan) her zaman yazılır;
     *   arama sayfasının özet çipi ve tüm sayfalar aynı seti görür.
     * - Hedef motorun paketi kanonik setten türetilerek güncellenir; paketin
     *   kanonik karşılığı olmayan alanlarına (format, durum, ülke ...) DOKUNULMAZ.
     * - `ALL` hedefinde yedi kaynağın paketi de güncellenir; "Tümü" aramasında
     *   seçilen türler artık yalnızca AniList'e değil, çevrilebilen tüm kaynaklara
     *   uygulanır.
     */
    fun applyTo(
        state: SearchUiState,
        target: SearchSourceEngine,
        common: CommonSearchFilters,
        scope: SearchScope = state.selectedScope
    ): SearchUiState {
        val base = state.copy(
            genres = common.genres,
            excludedGenres = common.excludedGenres,
            tags = common.tags,
            // Kurum seçimleri de taşınır: sayfa değişince liste kaybolmaz,
            // yalnızca hedef kaynağın uygulayabildiği kimlikler aramaya girer.
            institutions = common.institutions,
            startYear = common.startYear,
            endYear = common.endYear,
            minScore = common.minScore,
            maxScore = common.maxScore
        )
        return when (target) {
            SearchSourceEngine.ALL -> base.copy(
                aniListSpecificFilters = projectAniList(base.aniListSpecificFilters, common, scope),
                malSpecificFilters = projectMal(base.malSpecificFilters, common, scope),
                tmdbSpecificFilters = projectTmdb(base.tmdbSpecificFilters, common, scope),
                shikimoriSpecificFilters = projectShikimori(base.shikimoriSpecificFilters, common, scope),
                kitsuSpecificFilters = projectKitsu(base.kitsuSpecificFilters, common, scope),
                simklSpecificFilters = projectSimkl(base.simklSpecificFilters, common, scope),
                bangumiSpecificFilters = projectBangumi(base.bangumiSpecificFilters, common, scope)
            )
            SearchSourceEngine.ANILIST -> base.copy(
                aniListSpecificFilters = projectAniList(base.aniListSpecificFilters, common, scope)
            )
            SearchSourceEngine.MAL -> base.copy(
                malSpecificFilters = projectMal(base.malSpecificFilters, common, scope)
            )
            SearchSourceEngine.TMDB -> base.copy(
                tmdbSpecificFilters = projectTmdb(base.tmdbSpecificFilters, common, scope)
            )
            SearchSourceEngine.SHIKIMORI -> base.copy(
                shikimoriSpecificFilters = projectShikimori(base.shikimoriSpecificFilters, common, scope)
            )
            SearchSourceEngine.KITSU -> base.copy(
                kitsuSpecificFilters = projectKitsu(base.kitsuSpecificFilters, common, scope)
            )
            SearchSourceEngine.SIMKL -> base.copy(
                simklSpecificFilters = projectSimkl(base.simklSpecificFilters, common, scope)
            )
            SearchSourceEngine.BANGUMI -> base.copy(
                bangumiSpecificFilters = projectBangumi(base.bangumiSpecificFilters, common, scope)
            )
        }
    }

    /**
     * Sıralama çakışmasını çözer: hedef motorun MEVCUT sıralaması, gelen kanonik
     * anahtarın bu motordaki karşılığıyla aynı anlama geliyorsa mevcut anahtar
     * korunur. Böylece "revenue.desc", "votes", "match", "rank" gibi kanonik
     * karşılığı olmayan ama aynı kanonik anahtara çevrilen motor-özel sıralamalar
     * kendi sayfalarında ezilmez (ör. TMDB'de "Hasılat" seçimi, ortak çatı
     * yayılımı sırasında "Popülerlik"e düşürülüyordu).
     */
    private fun resolveSort(
        engine: SearchSourceEngine,
        currentSort: String,
        canonical: String?,
        mapped: String?
    ): String {
        if (mapped == null) return currentSort
        if (canonical == null) return mapped
        val currentCanonical = canonicalSortKey(engine, currentSort)
        return if (currentCanonical == canonical) currentSort else mapped
    }

    private fun isMangaScope(scope: SearchScope): Boolean = scope == SearchScope.MANGA ||
        scope == SearchScope.MANHWA || scope == SearchScope.MANHUA ||
        scope == SearchScope.LIGHT_NOVEL

    private fun isTvScope(scope: SearchScope): Boolean =
        scope == SearchScope.TV || scope == SearchScope.K_DRAMA

    private fun projectAniList(
        filters: AniListSpecificFilters,
        common: CommonSearchFilters,
        scope: SearchScope
    ): AniListSpecificFilters {
        val officialGenres = common.genres.filter { CanonicalFilterBridge.isOfficialAniListGenre(it) }
        // Demografi/tema adları AniList'te "tag" olarak aranır.
        val looseGenres = common.genres.filterNot { CanonicalFilterBridge.isOfficialAniListGenre(it) }
        val projectedTags = (looseGenres + common.tags).distinct()
        val projectedExcluded = common.excludedGenres
            .filter { CanonicalFilterBridge.isOfficialAniListGenre(it) }
        return filters.copy(
            genres = officialGenres,
            excludedGenres = projectedExcluded,
            tags = projectedTags,
            startYear = common.startYear,
            endYear = common.endYear,
            minScore = common.minScore,
            maxScore = common.maxScore,
            sort = resolveSort(
                SearchSourceEngine.ANILIST,
                filters.sort,
                common.sortKey,
                sortForEngine(SearchSourceEngine.ANILIST, common.sortKey)
            )
        )
    }

    private fun projectMal(
        filters: MalSpecificFilters,
        common: CommonSearchFilters,
        scope: SearchScope
    ): MalSpecificFilters {
        val isManga = isMangaScope(scope)
        // Etiketler de Jikan'da tür/anahtar kelime kimliğiyle aranabildiği için birleştirilir.
        val ids = (common.genres + common.tags)
            .mapNotNull { CanonicalFilterBridge.mapMalGenreId(it, isManga)?.toIntOrNull() }
            .distinct()
        val excludedIds = common.excludedGenres
            .mapNotNull { CanonicalFilterBridge.mapMalGenreId(it, isManga)?.toIntOrNull() }
            .distinct()
        val producerId = common.institutions
            .lastOrNull { it.engine == SearchSourceEngine.MAL && it.kind == CommonInstitutionKind.PRODUCER }
            ?.id
        val magazineId = common.institutions
            .lastOrNull { it.engine == SearchSourceEngine.MAL && it.kind == CommonInstitutionKind.MAGAZINE }
            ?.id
        val orderBy = resolveSort(
            SearchSourceEngine.MAL,
            filters.orderBy,
            common.sortKey,
            malOrderFor(common.sortKey)
        )
        // Jikan'da `order_by` tek başına yön belirtmez; API varsayılanı `desc`tir.
        // - Kanonik sıralama MAL'in mevcut `order_by` değerini DEĞİŞTİRDİYSE yön
        //   kanonik anahtardan türetilir (alfabetik → asc, diğerleri → desc).
        // - Değiştirmediyse kullanıcının MAL sayfasındaki asc/desc seçimi korunur.
        // Önceki hâlde "İsim: A-Z" Z→A geliyordu; ayrıca yön `asc` kalmışken
        // "En Yüksek Puanlı" seçilirse en düşük puanlılar geliyordu.
        val sortDirection = when {
            filters.orderBy == orderBy -> filters.sortDirection
            orderBy == "title" -> "asc"
            else -> "desc"
        }
        return filters.copy(
            genres = ids,
            excludedGenres = excludedIds,
            startYear = common.startYear,
            endYear = common.endYear,
            // Kanonik puan 0–100; Jikan/MAL `min_score` 0–10 kabul eder.
            minScore = canonicalScoreToTen(common.minScore),
            maxScore = canonicalScoreToTen(common.maxScore),
            producerId = producerId ?: filters.producerId,
            magazineId = magazineId ?: filters.magazineId,
            orderBy = orderBy,
            sortDirection = sortDirection
        )
    }

    private fun projectTmdb(
        filters: TmdbSpecificFilters,
        common: CommonSearchFilters,
        scope: SearchScope
    ): TmdbSpecificFilters {
        val isTv = isTvScope(scope)
        val ids = (common.genres + common.tags)
            .mapNotNull { CanonicalFilterBridge.mapTmdbGenreId(it, isTv) }
            .distinct()
        val excludedIds = common.excludedGenres
            .mapNotNull { CanonicalFilterBridge.mapTmdbGenreId(it, isTv) }
            .distinct()
        val companyId = common.institutions
            .lastOrNull { it.engine == SearchSourceEngine.TMDB && it.kind == CommonInstitutionKind.COMPANY }
            ?.id
        val networkId = common.institutions
            .lastOrNull { it.engine == SearchSourceEngine.TMDB && it.kind == CommonInstitutionKind.NETWORK }
            ?.id
        return filters.copy(
            genres = ids,
            excludedGenres = excludedIds,
            startYear = common.startYear,
            endYear = common.endYear,
            // Kanonik puan 0–100; TMDB `vote_average` 0–10 aralığındadır.
            minScore = canonicalScoreToTen(common.minScore),
            maxScore = canonicalScoreToTen(common.maxScore),
            companyId = companyId ?: filters.companyId,
            networkId = networkId ?: filters.networkId,
            sortBy = resolveSort(
                SearchSourceEngine.TMDB,
                filters.sortBy,
                common.sortKey,
                tmdbSortFor(common.sortKey)
            )
        )
    }

    private fun projectShikimori(
        filters: ShikimoriSpecificFilters,
        common: CommonSearchFilters,
        scope: SearchScope
    ): ShikimoriSpecificFilters {
        val ids = (common.genres + common.tags)
            .mapNotNull { CanonicalFilterBridge.mapAnimeGenreId(it) }
            .distinct()
        val excludedIds = common.excludedGenres
            .mapNotNull { CanonicalFilterBridge.mapAnimeGenreId(it) }
            .distinct()
        return filters.copy(
            genres = ids,
            excludedGenres = excludedIds,
            // Shikimori yıl filtresini "2024" biçimindeki season parametresiyle alır.
            season = filters.season ?: common.startYear?.toString(),
            // Shikimori `score` filtresi 1–9 (0–10 ölçeği) bekler.
            minScore = canonicalScoreToTenInt(common.minScore),
            order = resolveSort(
                SearchSourceEngine.SHIKIMORI,
                filters.order,
                common.sortKey,
                shikimoriOrderFor(common.sortKey)
            )
        )
    }

    private fun projectKitsu(
        filters: KitsuSpecificFilters,
        common: CommonSearchFilters,
        scope: SearchScope
    ): KitsuSpecificFilters {
        val categories = (common.genres + common.tags)
            .map { kitsuCategorySlug(it) }
            .filter { it.isNotBlank() }
            .distinct()
        return filters.copy(
            categories = categories,
            seasonYear = filters.seasonYear ?: common.startYear,
            minRating = common.minScore,
            sort = resolveSort(
                SearchSourceEngine.KITSU,
                filters.sort,
                common.sortKey,
                kitsuSortFor(common.sortKey)
            )
        )
    }

    private fun projectSimkl(
        filters: SimklSpecificFilters,
        common: CommonSearchFilters,
        scope: SearchScope
    ): SimklSpecificFilters {
        // Simkl tek bir tür slug'ı kabul eder; ilk çevrilebilen tür kullanılır.
        val slug = (common.genres + common.tags)
            .firstNotNullOfOrNull { CanonicalFilterBridge.mapSimklGenre(it) }
        return filters.copy(
            genre = slug ?: filters.genre,
            year = filters.year ?: common.startYear?.toString(),
            sort = resolveSort(
                SearchSourceEngine.SIMKL,
                filters.sort,
                common.sortKey,
                simklSortFor(common.sortKey)
            )
        )
    }

    private fun projectBangumi(
        filters: BangumiSpecificFilters,
        common: CommonSearchFilters,
        scope: SearchScope
    ): BangumiSpecificFilters {
        // Bangumi etiket sözlüğü yerel (CJK) yazımlarla çalışır; yalnızca kullanıcının
        // açıkça seçtiği etiketler taşınır. Tür adları çevrilemediği için eklenmez.
        val projectedTags = common.tags.filter { it.isNotBlank() }.distinct()
        return filters.copy(
            tags = projectedTags,
            yearFrom = common.startYear,
            yearTo = common.endYear,
            // Bangumi `rating` filtresi 0–10 ölçeğindedir.
            minScore = canonicalScoreToTenInt(common.minScore),
            sort = resolveSort(
                SearchSourceEngine.BANGUMI,
                filters.sort,
                common.sortKey,
                bangumiSortFor(common.sortKey)
            )
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3) SIRALAMA ÇEVİRİLERİ
    // ─────────────────────────────────────────────────────────────────────────

    /** Motorun kendi sıralama anahtarını kanonik (AniList) anahtara çevirir. */
    fun canonicalSortKey(engine: SearchSourceEngine, engineSort: String): String = when (engine) {
        SearchSourceEngine.ALL, SearchSourceEngine.ANILIST -> engineSort
        SearchSourceEngine.MAL -> when (engineSort) {
            "members" -> "POPULARITY_DESC"
            "score" -> "SCORE_DESC"
            "favorites" -> "FAVOURITES_DESC"
            "start_date" -> "START_DATE_DESC"
            "title" -> "TITLE_ROMAJI"
            else -> "POPULARITY_DESC"
        }
        SearchSourceEngine.TMDB -> when (engineSort) {
            "vote_average.desc" -> "SCORE_DESC"
            // Dizi keşfinde TMDB `primary_release_date` yerine `first_air_date`
            // kullanır; iki yazım da aynı kanonik anahtara çevrilir.
            "primary_release_date.desc", "first_air_date.desc" -> "START_DATE_DESC"
            "primary_release_date.asc", "first_air_date.asc" -> "START_DATE_ASC"
            // Hasılat sıralaması AniList/Kitsu gibi kaynaklarda karşılığı olmayan
            // ayrı bir anahtardır; POPULARITY'ye düşürülürse kullanıcının seçimi
            // kendi sayfasında bile eziliyordu.
            "revenue.desc" -> "REVENUE_DESC"
            else -> "POPULARITY_DESC"
        }
        SearchSourceEngine.SHIKIMORI -> when (engineSort) {
            "ranked" -> "SCORE_DESC"
            "aired_on" -> "START_DATE_DESC"
            "name" -> "TITLE_ROMAJI"
            else -> "POPULARITY_DESC"
        }
        SearchSourceEngine.KITSU -> when (engineSort) {
            "-userCount" -> "POPULARITY_DESC"
            "-averageRating" -> "SCORE_DESC"
            "-createdAt" -> "START_DATE_DESC"
            // JSON:API'de `-` öneki azalan, öneksiz hâli artan sıralamadır.
            "createdAt" -> "START_DATE_ASC"
            else -> "TRENDING_DESC"
        }
        SearchSourceEngine.SIMKL -> when (engineSort) {
            "rating" -> "SCORE_DESC"
            else -> "POPULARITY_DESC"
        }
        SearchSourceEngine.BANGUMI -> when (engineSort) {
            "heat" -> "POPULARITY_DESC"
            "rank" -> "POPULARITY_DESC"
            "score" -> "SCORE_DESC"
            "match" -> "SEARCH_MATCH"
            else -> "SEARCH_MATCH"
        }
    }

    /** Kanonik sıralama anahtarını hedef motorun anahtarına çevirir. */
    fun sortForEngine(engine: SearchSourceEngine, canonical: String?): String? {
        val key = canonical?.takeIf { it.isNotBlank() } ?: return null
        return when (engine) {
            SearchSourceEngine.ALL, SearchSourceEngine.ANILIST ->
                key.takeIf { it in aniListSortKeys }
            SearchSourceEngine.MAL -> malOrderFor(key)
            SearchSourceEngine.TMDB -> tmdbSortFor(key)
            SearchSourceEngine.SHIKIMORI -> shikimoriOrderFor(key)
            SearchSourceEngine.KITSU -> kitsuSortFor(key)
            SearchSourceEngine.SIMKL -> simklSortFor(key)
            SearchSourceEngine.BANGUMI -> bangumiSortFor(key)
        }
    }

    private fun malOrderFor(canonical: String?): String? = when (canonical) {
        "POPULARITY_DESC", "TRENDING_DESC", "SEARCH_MATCH" -> "members"
        "SCORE_DESC" -> "score"
        "FAVOURITES_DESC" -> "favorites"
        "START_DATE_DESC", "START_DATE_ASC" -> "start_date"
        "TITLE_ROMAJI", "TITLE_ROMAJI_DESC", "TITLE_ENGLISH", "TITLE_NATIVE" -> "title"
        else -> null
    }

    private fun tmdbSortFor(canonical: String?): String? = when (canonical) {
        "SCORE_DESC" -> "vote_average.desc"
        // `START_DATE_ASC` eskiden de azalan sıralamaya çevriliyordu; "En Eskiler"
        // seçildiğinde en yeni kayıtlar geliyordu. Yön artık korunur.
        "START_DATE_DESC" -> "primary_release_date.desc"
        "START_DATE_ASC" -> "primary_release_date.asc"
        "REVENUE_DESC" -> "revenue.desc"
        "POPULARITY_DESC", "TRENDING_DESC", "FAVOURITES_DESC", "SEARCH_MATCH" -> "popularity.desc"
        else -> null
    }

    private fun shikimoriOrderFor(canonical: String?): String? = when (canonical) {
        "SCORE_DESC" -> "ranked"
        "START_DATE_DESC", "START_DATE_ASC" -> "aired_on"
        "TITLE_ROMAJI", "TITLE_ROMAJI_DESC" -> "name"
        "POPULARITY_DESC", "TRENDING_DESC", "FAVOURITES_DESC", "SEARCH_MATCH" -> "popularity"
        else -> null
    }

    private fun kitsuSortFor(canonical: String?): String? = when (canonical) {
        "POPULARITY_DESC", "FAVOURITES_DESC", "SEARCH_MATCH" -> "-userCount"
        "SCORE_DESC" -> "-averageRating"
        "START_DATE_DESC" -> "-createdAt"
        "START_DATE_ASC" -> "createdAt"
        "TRENDING_DESC" -> "trending"
        else -> null
    }

    private fun simklSortFor(canonical: String?): String? = when (canonical) {
        "SCORE_DESC" -> "rating"
        "POPULARITY_DESC", "TRENDING_DESC", "SEARCH_MATCH" -> "votes"
        else -> null
    }

    private fun bangumiSortFor(canonical: String?): String? = when (canonical) {
        "SCORE_DESC" -> "score"
        "POPULARITY_DESC", "FAVOURITES_DESC", "TRENDING_DESC" -> "heat"
        // Bangumi'nin varsayılanı "match"tir; ters çevrimde "rank"e düşürmek
        // kullanıcının seçtiği "En İyi Eşleşme" anahtarını eziyordu.
        "SEARCH_MATCH" -> "match"
        else -> null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4) TÜR ADI / KİMLİK ÇEVİRİLERİ
    // ─────────────────────────────────────────────────────────────────────────

    /** Kitsu `filter[categories]` için slug üretimi (ör. "Slice of Life" → "slice-of-life"). */
    fun kitsuCategorySlug(name: String): String = name
        .lowercase(Locale.ROOT)
        .replace("&", "and")
        .replace("'", "")
        .trim()
        .replace(Regex("\\s+"), "-")

    /**
     * Kitsu kategori slug'ı → kanonik ad ("slice-of-life" → "Slice of Life").
     * [kitsuCategorySlug]'ın tersidir; kısa bağlaçlar küçük harfte bırakılır.
     */
    fun kitsuSlugToName(slug: String): String {
        val words = slug.split('-', '_').filter { it.isNotBlank() }
        if (words.isEmpty()) return slug
        val minor = setOf("of", "and", "the", "in", "a", "to", "for", "with", "on")
        return words.mapIndexed { index, word ->
            val lower = word.lowercase(Locale.ROOT)
            if (index > 0 && lower in minor) lower else lower.replaceFirstChar { it.uppercase(Locale.ROOT) }
        }.joinToString(" ")
    }

    /** Jikan / MAL tür kimliği → kanonik İngilizce ad. */
    fun malGenreName(id: Int): String? = when (id) {
        1 -> "Action"
        2 -> "Adventure"
        4 -> "Comedy"
        5 -> "Avant Garde"
        7 -> "Mystery"
        8 -> "Drama"
        9 -> "Ecchi"
        10 -> "Fantasy"
        12 -> "Hentai"
        13 -> "Historical"
        14 -> "Horror"
        15 -> "Kids"
        17 -> "Martial Arts"
        18 -> "Mecha"
        19 -> "Music"
        22 -> "Romance"
        23 -> "School"
        24 -> "Sci-Fi"
        25 -> "Shoujo"
        27 -> "Shounen"
        30 -> "Sports"
        31 -> "Super Power"
        36 -> "Slice of Life"
        37 -> "Supernatural"
        38 -> "Military"
        39 -> "Detective"
        40 -> "Psychological"
        41 -> "Suspense"
        42 -> "Seinen"
        43 -> "Josei"
        46 -> "Award Winning"
        48 -> "Workplace"
        62 -> "Isekai"
        else -> null
    }

    /** TMDB tür kimliği → kanonik İngilizce ad. */
    fun tmdbGenreName(id: Int, isTv: Boolean): String? = when (id) {
        28 -> "Action"
        12 -> "Adventure"
        16 -> "Animation"
        35 -> "Comedy"
        80 -> "Crime"
        99 -> "Documentary"
        18 -> "Drama"
        10751 -> "Family"
        14 -> if (isTv) null else "Fantasy"
        10765 -> "Sci-Fi"
        36 -> "History"
        27 -> "Horror"
        10402 -> "Music"
        9648 -> "Mystery"
        10749 -> "Romance"
        878 -> "Sci-Fi"
        10770 -> "TV Movie"
        53 -> "Thriller"
        10752 -> "War"
        37 -> "Western"
        10759 -> "Action"
        else -> null
    }

    /** Simkl tür slug'ı → kanonik ad. */
    fun simklGenreName(slug: String): String? = when (slug.lowercase(Locale.ROOT)) {
        "action" -> "Action"
        "adventure" -> "Adventure"
        "comedy" -> "Comedy"
        "drama" -> "Drama"
        "fantasy" -> "Fantasy"
        "sci-fi" -> "Sci-Fi"
        "romance" -> "Romance"
        "supernatural" -> "Supernatural"
        "mystery" -> "Mystery"
        "horror" -> "Horror"
        "sports" -> "Sports"
        "slice-of-life" -> "Slice of Life"
        else -> null
    }
}

/**
 * "Tümünü Gör" geçişinde filtre devri.
 *
 * Arama sayfası ve kaynağa özel sayfa AYRI ViewModel örnekleri kullandığı için
 * kanonik filtre seti navigasyondan hemen önce buraya bırakılır; hedef sayfa
 * açılışta bir kez tüketir. Süreç ölürse devir kaybolur, ancak uygulama yeniden
 * açıldığında seçimler zaten sıfırdan yapılacağı için bu kabul edilebilir.
 */
object SearchFilterHandoff {
    @Volatile
    private var pending: CommonSearchFilters? = null

    fun publish(filters: CommonSearchFilters) {
        pending = filters
    }

    fun consume(): CommonSearchFilters? {
        val value = pending
        pending = null
        return value
    }

    fun peek(): CommonSearchFilters? = pending

    fun clear() {
        pending = null
    }
}
