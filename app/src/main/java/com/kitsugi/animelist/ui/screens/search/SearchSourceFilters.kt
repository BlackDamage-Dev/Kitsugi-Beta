package com.kitsugi.animelist.ui.screens.search

/**
 * 7 Ana Katalog Arama Motoru
 * Arama çubuğunun sağ iç tarafındaki Brave Tarzı Seçici bu seçenekleri listeler.
 */
enum class SearchSourceEngine(val id: String, val label: String, val emoji: String) {
    ALL("all", "Tümü", "🌐"),
    ANILIST("anilist", "AniList", "🅰️"),
    MAL("mal", "MyAnimeList", "Ⓜ️"),
    TMDB("tmdb", "TMDB", "🎬"),
    SHIKIMORI("shikimori", "Shikimori", "🌸"),
    KITSU("kitsu", "Kitsu", "🦊"),
    SIMKL("simkl", "Simkl", "📺"),
    BANGUMI("bangumi", "Bangumi", "🎌");

    val displayLabel: String get() = "$emoji $label"
    val shortLabel: String get() = when (this) {
        ALL -> "🌐 Tümü"
        ANILIST -> "🅰️ AL"
        MAL -> "Ⓜ️ MAL"
        TMDB -> "🎬 TMDB"
        SHIKIMORI -> "🌸 SHI"
        KITSU -> "🦊 KTS"
        SIMKL -> "📺 SMK"
        BANGUMI -> "🎌 BGM"
    }

    companion object {
        fun fromId(id: String): SearchSourceEngine? =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }
}

/**
 * Her kaynağın altında çıkan alt kapsam sekmeleri (Anime, Manga, Dizi, Karakter vb.)
 */
enum class SearchScope(val id: String, val label: String, val emoji: String = "") {
    ALL_MIXED("all_mixed", "Tümü", "🌟"),
    ANIME("anime", "Anime", "⚡"),
    MANGA("manga", "Manga", "📖"),
    MANHWA("manhwa", "Manhwa", "🇰🇷"),
    MANHUA("manhua", "Manhua", "🇨🇳"),
    LIGHT_NOVEL("light_novel", "Light Novel", "📚"),
    TV("tv", "Diziler", "📺"),
    MOVIE("movie", "Filmler", "🎬"),
    K_DRAMA("k_drama", "K-Drama", "🫰"),
    CHARACTER("character", "Karakterler", "👤"),
    STAFF("staff", "Seslendirmen & Ekip", "🎙️"),
    STUDIO("studio", "Stüdyolar", "🏢");

    val displayLabel: String get() = if (emoji.isNotBlank()) "$emoji $label" else label

    fun supportsEngine(engine: SearchSourceEngine): Boolean = this in engine.availableScopes()
}

/**
 * Bir kaynak motoru seçildiğinde arama çubuğunun altında listelenecek alt kapsamlar.
 */
fun SearchSourceEngine.availableScopes(): List<SearchScope> = when (this) {
    SearchSourceEngine.ALL -> listOf(
        SearchScope.ALL_MIXED, SearchScope.ANIME, SearchScope.MANGA,
        SearchScope.TV, SearchScope.MOVIE, SearchScope.CHARACTER, SearchScope.STAFF
    )
    SearchSourceEngine.ANILIST -> listOf(
        SearchScope.ALL_MIXED, SearchScope.ANIME, SearchScope.MANGA, SearchScope.MANHWA, SearchScope.MANHUA,
        SearchScope.LIGHT_NOVEL, SearchScope.CHARACTER, SearchScope.STAFF, SearchScope.STUDIO
    )
    SearchSourceEngine.MAL -> listOf(
        SearchScope.ALL_MIXED, SearchScope.ANIME, SearchScope.MANGA, SearchScope.MANHWA, SearchScope.MANHUA,
        SearchScope.LIGHT_NOVEL, SearchScope.CHARACTER, SearchScope.STAFF, SearchScope.STUDIO
    )
    SearchSourceEngine.TMDB -> listOf(
        SearchScope.ALL_MIXED, SearchScope.TV, SearchScope.MOVIE,
        SearchScope.ANIME, SearchScope.K_DRAMA, SearchScope.STAFF, SearchScope.STUDIO
    )
    SearchSourceEngine.SHIKIMORI -> listOf(
        SearchScope.ALL_MIXED, SearchScope.ANIME, SearchScope.MANGA, SearchScope.MANHWA, SearchScope.MANHUA,
        SearchScope.LIGHT_NOVEL, SearchScope.CHARACTER, SearchScope.STAFF
    )
    SearchSourceEngine.KITSU -> listOf(
        SearchScope.ALL_MIXED, SearchScope.ANIME, SearchScope.MANGA, SearchScope.MANHWA, SearchScope.MANHUA,
        SearchScope.LIGHT_NOVEL, SearchScope.CHARACTER
    )
    SearchSourceEngine.SIMKL -> listOf(
        SearchScope.ALL_MIXED, SearchScope.ANIME, SearchScope.TV, SearchScope.MOVIE
    )
    // Bangumi yalnız anime/manga değil 三次元 (type = 6) dizileri ve filmleri de indeksler;
    // sekmelerde bu kapsamlar yoktu, kullanıcı "Bangumi'de dizi/film araması" yapamıyordu.
    SearchSourceEngine.BANGUMI -> listOf(
        SearchScope.ALL_MIXED, SearchScope.ANIME, SearchScope.MANGA, SearchScope.MANHWA, SearchScope.MANHUA,
        SearchScope.LIGHT_NOVEL, SearchScope.TV, SearchScope.MOVIE, SearchScope.CHARACTER, SearchScope.STAFF
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// KAPSAM → İÇERİK AİLESİ EŞLEMESİ
// ─────────────────────────────────────────────────────────────────────────────

/** Yazılı içerik kapsamları (manga/manhwa/manhua/light novel). */
val SearchScope.isPrintScope: Boolean
    get() = this == SearchScope.MANGA || this == SearchScope.MANHWA ||
        this == SearchScope.MANHUA || this == SearchScope.LIGHT_NOVEL

/** Gerçek çekim (live-action) film/dizi kapsamları. */
val SearchScope.isLiveActionScope: Boolean
    get() = this == SearchScope.TV || this == SearchScope.MOVIE || this == SearchScope.K_DRAMA

/**
 * "Tümü" (çoklu motor) aramasında bu kapsamın anime veritabanlarını isteyip istemediği.
 *
 * Gerçek çekim kapsamlarında (Diziler/Filmler/K-Drama) anime veritabanları sorgulanmaz:
 * AniList/MAL/Shikimori/Kitsu/Bangumi bu kapsamlar için anime döndürüyordu ve
 * "Diziler" rafının altında anime kartları görünüyordu.
 */
val SearchScope.wantsAnimeSources: Boolean
    get() = this == SearchScope.ALL_MIXED || this == SearchScope.ANIME ||
        this == SearchScope.CHARACTER || this == SearchScope.STAFF || this == SearchScope.STUDIO

/** Bu kapsamın manga veritabanlarını isteyip istemediği. */
val SearchScope.wantsMangaSources: Boolean
    get() = this == SearchScope.ALL_MIXED || isPrintScope

/**
 * Bu kapsamın film/dizi (TMDB, Simkl) veritabanlarını isteyip istemediği.
 *
 * Manga/manhwa kapsamlarında TMDB ve Simkl sorgulanmaz: bu kaynaklar yalnızca
 * film/dizi/anime döndürebildiği için "Manhwa" aramasının altına alakasız
 * live-action filmler geliyordu. ANIME kapsamında da TMDB atlanır çünkü
 * `search/multi` ucu anime ile live-action uyarlamaları ayırt edemez; Simkl ise
 * `type=anime` süzgeci desteklediği için anime kapsamında aktif kalır.
 */
val SearchScope.wantsLiveActionSources: Boolean
    get() = this == SearchScope.ALL_MIXED || isLiveActionScope

/** Simkl arama ucuna gönderilecek tür süzgeci (`null` = tüm türler). */
val SearchScope.simklTypeFilter: String?
    get() = when (this) {
        SearchScope.ANIME -> "anime"
        SearchScope.TV, SearchScope.K_DRAMA -> "tv"
        SearchScope.MOVIE -> "movie"
        else -> null
    }

/**
 * "Tümü" (çoklu motor) aramasında hangi kaynağın gerçekten sorgulanacağı.
 *
 * `false` olan kaynak için ağ isteği hiç yapılmaz; rafı boş + "yükleniyor değil"
 * durumda kalır ve çizilmez. Böylece kapsamın üretemediği içerik ailesi rafta
 * alakasız kayıt olarak görünmez (örn. Manhwa aramasının altında TMDB filmleri).
 */
internal data class AllSourcePlan(
    val aniList: Boolean,
    val mal: Boolean,
    val tmdb: Boolean,
    val shikimori: Boolean,
    val kitsu: Boolean,
    val simkl: Boolean,
    val bangumi: Boolean
) {
    /** Yalnızca sorgulanacak kaynaklar "yükleniyor" işaretlenir (titremeyi önler). */
    fun toLoadingState(): MultiPlatformResults = MultiPlatformResults.loading(
        aniList = aniList,
        mal = mal,
        tmdb = tmdb,
        shikimori = shikimori,
        kitsu = kitsu,
        simkl = simkl,
        bangumi = bangumi
    )

    /** İki planın kesişimi (kapsam planı ∧ filtre/facet planı). */
    fun intersect(other: AllSourcePlan): AllSourcePlan = copy(
        aniList = aniList && other.aniList,
        mal = mal && other.mal,
        tmdb = tmdb && other.tmdb,
        shikimori = shikimori && other.shikimori,
        kitsu = kitsu && other.kitsu,
        simkl = simkl && other.simkl,
        bangumi = bangumi && other.bangumi
    )

    /**
     * Bu aramada gerçekten sorgulanan kaynakların `ListPlatform.fromLabel` ile
     * çözülebilen kimlikleri. Arama hata rozetleri yalnızca bu kümeyle sınırlanır;
     * arkada çalışan senkronizasyon/keşfet isteklerinin hataları arama ekranına sızmaz.
     */
    fun activePlatformIds(): Set<String> = buildSet {
        if (aniList) add(SearchSourceEngine.ANILIST.id)
        if (mal) add(SearchSourceEngine.MAL.id)
        if (tmdb) add(SearchSourceEngine.TMDB.id)
        if (shikimori) add(SearchSourceEngine.SHIKIMORI.id)
        if (kitsu) add(SearchSourceEngine.KITSU.id)
        if (simkl) add(SearchSourceEngine.SIMKL.id)
        if (bangumi) add(SearchSourceEngine.BANGUMI.id)
    }

    /** Hiçbir kaynak sorgulanmayacak mı? (Filtre hiçbir kaynakta temsil edilemiyor.) */
    val isEmpty: Boolean
        get() = !aniList && !mal && !tmdb && !shikimori && !kitsu && !simkl && !bangumi

    val anyActive: Boolean
        get() = !isEmpty

    /** Kapsamın normalde desteklediği ama mevcut filtreler yüzünden atlanan kaynaklar. */
    fun skippedEngines(scope: SearchScope): List<SearchSourceEngine> {
        val base = planAllSources(scope)
        return buildList {
            if (base.aniList && !aniList) add(SearchSourceEngine.ANILIST)
            if (base.mal && !mal) add(SearchSourceEngine.MAL)
            if (base.tmdb && !tmdb) add(SearchSourceEngine.TMDB)
            if (base.shikimori && !shikimori) add(SearchSourceEngine.SHIKIMORI)
            if (base.kitsu && !kitsu) add(SearchSourceEngine.KITSU)
            if (base.simkl && !simkl) add(SearchSourceEngine.SIMKL)
            if (base.bangumi && !bangumi) add(SearchSourceEngine.BANGUMI)
        }
    }
}

internal fun planAllSources(scope: SearchScope): AllSourcePlan {
    // Anime/manga veritabanları gerçek çekim kapsamlarında hiç çalışmaz.
    val mediaDb = (scope.wantsAnimeSources || scope.wantsMangaSources) && !scope.isLiveActionScope
    return AllSourcePlan(
        aniList = mediaDb,
        mal = mediaDb,
        // TMDB'nin search/multi ucu anime ile live-action uyarlamayı ayırt edemez;
        // bu yüzden yalnızca film/dizi kapsamlarında sorgulanır.
        tmdb = scope.wantsLiveActionSources,
        shikimori = mediaDb,
        kitsu = mediaDb,
        // Simkl tür süzgeci (anime/tv/movie) desteklediği için anime kapsamında da aktif.
        simkl = scope.wantsLiveActionSources || scope == SearchScope.ANIME,
        // Bangumi yalnızca anime/manga değil, **三次元** (type = 6) dizileri ve filmleri de
        // barındırır (cat 6001/6002). `mediaDb` yalnızca anime/manga kapsamlarında true
        // olduğu için "Tümü" aramasında Diziler/Filmler rafında Bangumi hiç sorgulanmıyordu;
        // kullanıcı bunu "Bangumi dizileri/filmleri gelmiyor" olarak görüyordu.
        bangumi = mediaDb || scope.wantsLiveActionSources
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// KAYNAĞA ÖZEL FİLTRE MODELLERİ
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 🅰️ AniList Özel Filtreleri
 */
data class AniListSpecificFilters(
    val formats: List<String> = emptyList(),
    val statuses: List<String> = emptyList(),
    val season: String? = null,
    val seasonYear: Int? = null,
    val startYear: Int? = null,
    val endYear: Int? = null,
    val country: String? = null, // JP, KR, CN, TW
    val sources: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val excludedGenres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val excludedTags: List<String> = emptyList(),
    val minimumTagRank: Int? = null, // 0..100
    val minScore: Int? = null,
    val maxScore: Int? = null,
    val minEpCh: Int? = null,
    val maxEpCh: Int? = null,
    val minDuration: Int? = null,
    val maxDuration: Int? = null,
    val minVolumes: Int? = null,
    val maxVolumes: Int? = null,
    val licensedBy: List<String> = emptyList(),
    val onMyList: Boolean? = null,
    val isDoujin: Boolean? = null,
    val isAdult: Boolean? = null,
    val isBirthday: Boolean? = null, // Karakter/Staff için doğum günü
    val sort: String = "POPULARITY_DESC"
) {
    val isLicensed: Boolean?
        get() = when (isDoujin) {
            true -> false
            false -> true
            null -> null
        }

    val activeCount: Int get() {
        var count = 0
        if (formats.isNotEmpty()) count += formats.size
        if (statuses.isNotEmpty()) count += statuses.size
        if (season != null) count++
        if (seasonYear != null) count++
        if (startYear != null || endYear != null) count++
        if (country != null) count++
        if (sources.isNotEmpty()) count += sources.size
        if (genres.isNotEmpty()) count += genres.size
        if (excludedGenres.isNotEmpty()) count += excludedGenres.size
        if (tags.isNotEmpty()) count += tags.size
        if (excludedTags.isNotEmpty()) count += excludedTags.size
        if (minimumTagRank != null) count++
        if (minScore != null || maxScore != null) count++
        if (minEpCh != null || maxEpCh != null) count++
        if (minDuration != null || maxDuration != null) count++
        if (minVolumes != null || maxVolumes != null) count++
        if (licensedBy.isNotEmpty()) count += licensedBy.size
        if (onMyList != null) count++
        if (isDoujin != null) count++
        if (isAdult != null) count++
        if (isBirthday != null) count++
        return count
    }
}

/**
 * Ⓜ️ MyAnimeList (Jikan v4) Özel Filtreleri
 */
data class MalSpecificFilters(
    val orderBy: String = "popularity",
    val sortDirection: String = "desc",
    /** Multiple MAL types come from the common format multi-select; the legacy [type]
     * field remains the first value for callers that support only one type. */
    val types: List<String> = emptyList(),
    val type: String? = null,
    /** Jikan accepts one status per request; the effective bridge may fan out a multi-select. */
    val statuses: List<String> = emptyList(),
    val status: String? = null,
    val season: String? = null,
    val seasonYear: Int? = null,
    val rating: String? = null,
    val genres: List<Int> = emptyList(),
    val excludedGenres: List<Int> = emptyList(),
    val minScore: Double? = null,
    val maxScore: Double? = null,
    val startYear: Int? = null,
    val endYear: Int? = null,
    val producerId: Int? = null,
    val magazineId: Int? = null,
    val letter: String? = null
) {
    val activeCount: Int get() {
        var count = 0
        if (types.isNotEmpty() || type != null) count += maxOf(types.size, 1)
        if (statuses.isNotEmpty() || status != null) count += maxOf(statuses.size, 1)
        if (season != null) count++
        if (seasonYear != null) count++
        if (rating != null) count++
        if (genres.isNotEmpty()) count += genres.size
        if (excludedGenres.isNotEmpty()) count += excludedGenres.size
        if (minScore != null || maxScore != null) count++
        if (startYear != null || endYear != null) count++
        if (producerId != null) count++
        if (magazineId != null) count++
        if (letter != null) count++
        return count
    }
}

/**
 * 🎬 TMDB v3 Özel Filtreleri
 */
data class TmdbSpecificFilters(
    val isMovie: Boolean = true,
    val sortBy: String = "popularity.desc",
    val genres: List<Int> = emptyList(),
    val excludedGenres: List<Int> = emptyList(),
    val originCountry: String? = null,
    val tvStatus: String? = null,
    val tvType: String? = null,
    val startYear: Int? = null,
    val endYear: Int? = null,
    val minScore: Double? = null,
    val maxScore: Double? = null,
    val minVoteCount: Int? = null,
    val minRuntime: Int? = null,
    val maxRuntime: Int? = null,
    val watchProviderId: Int? = null,
    val networkId: Int? = null,
    /** Ortak filtre çatısındaki "Kurumlar" seçiminden gelen yapım şirketi kimliği. */
    val companyId: Int? = null,
    val keyword: String? = null,
    val includeAdult: Boolean = false
) {
    val activeCount: Int get() {
        var count = 0
        if (genres.isNotEmpty()) count += genres.size
        if (excludedGenres.isNotEmpty()) count += excludedGenres.size
        if (originCountry != null) count++
        if (tvStatus != null) count++
        if (tvType != null) count++
        if (startYear != null || endYear != null) count++
        if (minScore != null || maxScore != null) count++
        if (minVoteCount != null) count++
        if (minRuntime != null || maxRuntime != null) count++
        if (watchProviderId != null) count++
        if (networkId != null) count++
        if (companyId != null) count++
        if (keyword != null) count++
        if (includeAdult) count++
        return count
    }
}

/**
 * 🌸 Shikimori Özel Filtreleri
 */
data class ShikimoriSpecificFilters(
    val order: String = "popularity",
    val kinds: List<String> = emptyList(),
    val statuses: List<String> = emptyList(),
    val season: String? = null, // winter_2026, 2025, 202x, 199x
    val minScore: Int? = null,
    val duration: String? = null, // S, D, F
    val rating: String? = null, // g, pg, pg_13, r, r_plus, rx
    val genres: List<Int> = emptyList(),
    val excludedGenres: List<Int> = emptyList(),
    val studioId: Int? = null,
    val publisherId: Int? = null,
    val censored: Boolean = true,
    val peopleKind: String? = null // seyu, mangaka, producer
) {
    val activeCount: Int get() {
        var count = 0
        if (kinds.isNotEmpty()) count += kinds.size
        if (statuses.isNotEmpty()) count += statuses.size
        if (season != null) count++
        if (minScore != null) count++
        if (duration != null) count++
        if (rating != null) count++
        if (genres.isNotEmpty()) count += genres.size
        if (excludedGenres.isNotEmpty()) count += excludedGenres.size
        if (studioId != null) count++
        if (publisherId != null) count++
        if (!censored) count++
        if (peopleKind != null) count++
        return count
    }
}

/**
 * 🦊 Kitsu Özel Filtreleri
 */
data class KitsuSpecificFilters(
    val sort: String = "trending",
    val subtypes: List<String> = emptyList(),
    val statuses: List<String> = emptyList(),
    val season: String? = null,
    val seasonYear: Int? = null,
    val categories: List<String> = emptyList(),
    val ageRating: String? = null,
    val streamers: List<String> = emptyList(),
    val minRating: Int? = null // 5..100
) {
    val activeCount: Int get() {
        var count = 0
        if (subtypes.isNotEmpty()) count += subtypes.size
        if (statuses.isNotEmpty()) count += statuses.size
        if (season != null) count++
        if (seasonYear != null) count++
        if (categories.isNotEmpty()) count += categories.size
        if (ageRating != null) count++
        if (streamers.isNotEmpty()) count += streamers.size
        if (minRating != null) count++
        return count
    }
}

/**
 * 📺 Simkl Özel Filtreleri
 */
data class SimklSpecificFilters(
    val trendingPeriod: String = "today", // today, week, month, calendar
    val sort: String = "rank", // rank, popular-today, popular-this-week, votes, release-date, a-z
    val subtype: String? = null, // tv, movies, ovas, onas, specials
    /** Simkl accepts one genre per discovery path; this list is used for multi-select
     * requests and [genre] remains the backwards-compatible first value. */
    val genres: List<String> = emptyList(),
    val genre: String? = null, // action, comedy, drama, fantasy...
    val country: String? = null, // jp, kr, cn, us
    val year: String? = null // 2026, 2025, 2020s, 1990s
) {
    // NOT: Simkl arama ucu puan filtresi (score/rating) kabul etmez. Daha önce
    // pakette `minScore` alanı vardı ve hiçbir isteğe yazılmıyordu; "filtre var"
    // görünüp hiçbir şey yapmıyordu. Alan kaldırıldı.
    val activeCount: Int get() {
        var count = 0
        if (subtype != null) count++
        if (genres.isNotEmpty() || genre != null) count += maxOf(genres.size, 1)
        if (country != null) count++
        if (year != null) count++
        return count
    }
}

/**
 * 🎌 Bangumi (bgm.tv) Özel Filtreleri
 * `POST /v0/search/subjects` gövdesindeki `filter` alanıyla birebir hizalıdır.
 */
data class BangumiSpecificFilters(
    /** `match` | `heat` | `rank` | `score` */
    val sort: String = "match",
    val tags: List<String> = emptyList(),
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
    /**
     * Mevsim + yıl (`WINTER`, `SPRING`, `SUMMER`, `FALL`). Bangumi'de sezon alanı yok;
     * `air_date` çeyrek aralığına çevrilir (canlı prob V1–V5). Detay sayfasındaki
     * "Kış 2025" facetinden gelen bilgi burada taşınır, aksi hâlde sunucu tüm yılı süzer.
     */
    val season: String? = null,
    val seasonYear: Int? = null,
    val minScore: Int? = null,
    val nsfw: Boolean = false,
    val comicsOnly: Boolean = true,
    val career: List<String> = emptyList()
) {
    val activeCount: Int get() {
        var count = 0
        if (sort != "match") count++
        if (tags.isNotEmpty()) count += tags.size
        if (yearFrom != null || yearTo != null) count++
        if (season != null && seasonYear != null) count++
        if (minScore != null) count++
        if (nsfw) count++
        if (!comicsOnly) count++
        if (career.isNotEmpty()) count += career.size
        return count
    }
}
