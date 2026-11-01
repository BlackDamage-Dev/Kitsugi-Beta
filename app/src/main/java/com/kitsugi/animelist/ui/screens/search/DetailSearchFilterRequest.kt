package com.kitsugi.animelist.ui.screens.search

import com.kitsugi.animelist.model.MediaType

/**
 * Ayrıntı Sayfaları (Detail Pages) <-> Arama Sayfası (Search Page) Çift Yönlü Birleşik Mimari
 * Detay sayfasında tıklanan her metadata öğesi (Tür, Tema, Demografi, Etiket/Keyword,
 * Sezon+Yıl, Format, Durum, Kaynak, Ülke, Stüdyo, Dergi, Kanal, Sıralama) bu model üzerinden
 * SearchScreen / SearchViewModel'a aktarılır.
 */
data class DetailSearchFilterRequest(
    val source: String,                      // "anilist", "mal"/"jikan", "tmdb", "shikimori", "kitsu", "simkl", "bangumi"
    val mediaType: MediaType,                // Anime, Manga, Movie, TvShow
    val format: String? = null,              // TV, MOVIE, OVA, ONA, SPECIAL, MANGA, MANHWA, MANHUA, NOVEL, ONE_SHOT
    val status: String? = null,              // RELEASING, FINISHED, NOT_YET_RELEASED, CANCELLED, HIATUS
    val genre: String? = null,               // Tür (Action, Romance, Sci-Fi vb.)
    val theme: String? = null,               // Tema (Isekai, Mecha, Psychological, Time Travel, School vb.)
    val demographic: String? = null,         // Demografi (Shounen, Seinen, Shoujo, Josei, Kids)
    val tag: String? = null,                 // AniList Tag / Kitsu Category / TMDB Keyword adı
    val tagSource: String? = null,           // Etiketin özgün sağlayıcısı (özellikle Bangumi'nin yerel etiketleri)
    val keywordId: Int? = null,              // Yalnızca doğrulanmış TMDB Keyword ID (AniList tag ID kullanılmaz)
    val season: String? = null,              // WINTER, SPRING, SUMMER, FALL
    val year: Int? = null,                  // Sezon yılı veya başlangıç yılı (örn. 2024)
    val sourceMaterial: String? = null,      // ORIGINAL, MANGA, LIGHT_NOVEL, WEB_NOVEL, VISUAL_NOVEL, VIDEO_GAME
    val countryCode: String? = null,         // JP, KR, CN, TW, US, TR, GB vb.
    val languageCode: String? = null,        // ja, ko, zh, en, tr vb.
    val studioId: Int? = null,               // Stüdyo / Yapımcı / Şirket ID
    val studioName: String? = null,          // Stüdyo / Yapımcı adı
    val producerId: Int? = null,             // MAL/Jikan Producer veya Shikimori Studio/Publisher ID
    val magazineId: Int? = null,             // MAL/Jikan Manga Dergisi (Serialization) veya Shikimori Publisher ID
    val magazineName: String? = null,        // Dergi adı (örn. Weekly Shounen Jump)
    val networkId: Int? = null,              // TMDB TV Network ID (örn. Netflix, HBO, tvN, Tokyo MX)
    val networkName: String? = null,         // Kanal/Platform adı
    val streamerName: String? = null,        // Yayıncı platform (Crunchyroll, Netflix, Disney+, Bilibili vb.)
    val ageRating: String? = null,           // G, PG, PG-13, R, R+, Rx, TV-MA vb.
    val sortBy: String? = null               // Puan Sırası (#Rank) veya Popülerlik Sırası (#Popularity) tıklandığında
)

/** Mevsim bilgisi: arayüz etiketi ve kaynaklara gönderilecek kanonik değer. */
data class DetailSeasonMetadata(
    val apiSeason: String?,
    val year: Int?,
    val displayLabel: String?
)

/**
 * Farklı kaynaklardan gelen `Winter`, `Kış 2010` veya yalnızca başlangıç tarihi gibi
 * değerleri tek bir mevsim/yıl modeline indirger. Tarihten mevsim türetimi yalnızca
 * anime ayrıntılarında çağrılır; film/dizilerde yalnız yıl filtresi kullanılır.
 */
fun resolveDetailSeasonMetadata(
    rawSeason: String?,
    displayedSeason: String?,
    seasonYear: Int? = null,
    startDate: String? = null,
    fallbackYear: Int? = null,
    deriveFromDate: Boolean = false
): DetailSeasonMetadata {
    val seasonText = listOfNotNull(rawSeason, displayedSeason)
        .firstOrNull { it.isNotBlank() }
        .orEmpty()
    val combinedText = listOfNotNull(rawSeason, displayedSeason)
        .filter { it.isNotBlank() }
        .joinToString(" ")
    val normalizedText = java.text.Normalizer.normalize(
        combinedText.lowercase(java.util.Locale.ROOT),
        java.text.Normalizer.Form.NFD
    ).replace(Regex("\\p{Mn}+"), "")
    var season = when {
        Regex("\\bwinter\\b|kis").containsMatchIn(normalizedText) -> "WINTER"
        Regex("\\bspring\\b|ilkbahar|bahar").containsMatchIn(normalizedText) -> "SPRING"
        Regex("\\bsummer\\b|yaz").containsMatchIn(normalizedText) -> "SUMMER"
        Regex("\\bfall\\b|\\bautumn\\b|sonbahar|guz").containsMatchIn(normalizedText) -> "FALL"
        else -> null
    }
    var resolvedYear = seasonYear?.takeIf { it > 0 }
        ?: Regex("(?:19|20)\\d{2}").find(combinedText)?.value?.toIntOrNull()
        ?: startDate?.take(4)?.toIntOrNull()?.takeIf { it > 0 }
        ?: fallbackYear?.takeIf { it > 0 }

    if (season == null && deriveFromDate) {
        val month = startDate?.split('-')?.getOrNull(1)?.toIntOrNull()
        season = when (month) {
            in 1..3 -> "WINTER"
            in 4..6 -> "SPRING"
            in 7..9 -> "SUMMER"
            in 10..12 -> "FALL"
            else -> null
        }
    }

    val seasonLabel = when (season) {
        "WINTER" -> "Kış"
        "SPRING" -> "İlkbahar"
        "SUMMER" -> "Yaz"
        "FALL" -> "Sonbahar"
        else -> null
    }
    val display = displayedSeason?.takeIf { it.isNotBlank() }
        ?: seasonLabel?.let { label -> if (resolvedYear != null) "$label $resolvedYear" else label }
        ?: seasonText.takeIf { it.isNotBlank() }

    return DetailSeasonMetadata(season, resolvedYear, display)
}

/**
 * 7 Kaynak ve Tümü için Kanonik Filtre Köprüsü (CanonicalFilterBridge)
 * Kaynaklar arası filtre çevirisi ve Akıllı Kapsam Koruması (Auto-Scope Guard)
 */
object CanonicalFilterBridge {

    fun sourceToEngine(source: String): SearchSourceEngine = when (source.lowercase().trim()) {
        "anilist" -> SearchSourceEngine.ANILIST
        "mal", "jikan" -> SearchSourceEngine.MAL
        "tmdb" -> SearchSourceEngine.TMDB
        "shikimori" -> SearchSourceEngine.SHIKIMORI
        "kitsu" -> SearchSourceEngine.KITSU
        "simkl" -> SearchSourceEngine.SIMKL
        "bangumi", "bgm" -> SearchSourceEngine.BANGUMI
        else -> SearchSourceEngine.ALL
    }

    fun normalizeSeason(value: String?): String? {
        val normalized = java.text.Normalizer.normalize(
            value?.trim()?.lowercase(java.util.Locale.ROOT).orEmpty(),
            java.text.Normalizer.Form.NFD
        ).replace(Regex("\\p{Mn}+"), "")
        return when {
            Regex("\\bwinter\\b|kis").containsMatchIn(normalized) -> "WINTER"
            Regex("\\bspring\\b|ilkbahar|bahar").containsMatchIn(normalized) -> "SPRING"
            Regex("\\bsummer\\b|yaz").containsMatchIn(normalized) -> "SUMMER"
            Regex("\\bfall\\b|\\bautumn\\b|sonbahar|guz").containsMatchIn(normalized) -> "FALL"
            else -> null
        }
    }

    fun isTmdbTagSource(source: String?): Boolean = source?.trim()?.lowercase(java.util.Locale.ROOT) in setOf(
        "tmdb", "themoviedb", "the movie database"
    )

    fun isOfficialAniListGenre(value: String): Boolean {
        val officialGenres = setOf(
            "Action", "Adventure", "Comedy", "Drama", "Ecchi", "Fantasy", "Hentai", "Horror",
            "Mahou Shoujo", "Mecha", "Music", "Mystery", "Psychological", "Romance", "Sci-Fi",
            "Slice of Life", "Sports", "Supernatural", "Thriller"
        )
        return officialGenres.any { it.equals(SearchTranslation.translateToEnglishForSearch(value).trim(), ignoreCase = true) }
    }

    /** Jikan/MAL anime genre/theme taxonomy, also used by Shikimori's matching IDs. */
    fun mapAnimeGenreId(genreOrTag: String?): Int? {
        val value = genreOrTag?.let(SearchTranslation::translateToEnglishForSearch) ?: return null
        return when (value.lowercase(java.util.Locale.ROOT).trim()) {
            "action" -> 1
            "adventure" -> 2
            "racing" -> 3
            "comedy" -> 4
            "avant garde" -> 5
            "mythology" -> 6
            "mystery" -> 7
            "drama" -> 8
            "ecchi" -> 9
            "fantasy", "magic" -> 10
            "strategy game" -> 11
            "hentai" -> 12
            "historical" -> 13
            "horror" -> 14
            "kids" -> 15
            "martial arts" -> 17
            "mecha" -> 18
            "music" -> 19
            "parody" -> 20
            "samurai" -> 21
            "romance" -> 22
            "school" -> 23
            "sci-fi", "cyberpunk" -> 24
            "shoujo ai", "yuri" -> 25
            "shounen ai", "boys' love", "boys love" -> 26
            "space", "space opera" -> 29
            "shounen" -> 27
            "sports" -> 30
            "super power", "superhero" -> 31
            "vampire" -> 32
            "harem" -> 33
            "slice of life", "iyashikei" -> 36
            "supernatural", "youkai" -> 37
            "military" -> 38
            "detective" -> 39
            "psychological" -> 40
            "suspense", "thriller" -> 41
            "seinen" -> 42
            "josei" -> 43
            "gourmet" -> 47
            "workplace", "work" -> 48
            "adult cast" -> 50
            "cgdct", "cute girls doing cute things" -> 52
            "childcare" -> 53
            "combat sports" -> 54
            "delinquents" -> 56
            "educational" -> 57
            "gag humor", "surreal comedy" -> 58
            "gore", "body horror" -> 59
            "high stakes game", "death game" -> 60
            "idols" -> 61
            "isekai" -> 62
            "love polygon", "love triangle" -> 64
            "medicine", "medical" -> 66
            "organized crime", "mafia", "yakuza", "criminal organization" -> 67
            "otaku culture" -> 68
            "performing arts", "showbiz" -> 69
            "pets", "animals" -> 70
            "reincarnation" -> 71
            "reverse harem" -> 72
            "survival", "post-apocalyptic" -> 75
            "time travel", "time loop", "time manipulation" -> 77
            "video games", "video game", "e-sports" -> 79
            "visual arts", "photography", "drawing" -> 80
            "shoujo" -> 25
            else -> null
        }
    }

    /** Simkl's category path only accepts this finite set of genre slugs. */
    fun mapSimklGenre(value: String?): String? {
        val normalized = value?.let(SearchTranslation::translateToEnglishForSearch)
            ?.lowercase(java.util.Locale.ROOT)?.trim() ?: return null
        return when (normalized) {
            "action", "aksiyon" -> "action"
            "adventure", "macera" -> "adventure"
            "comedy", "komedi" -> "comedy"
            "drama", "dram" -> "drama"
            "fantasy", "fantastik" -> "fantasy"
            "science fiction", "sci fi", "sci-fi", "bilim kurgu" -> "sci-fi"
            "romance", "romantik", "romantizm" -> "romance"
            "supernatural", "dogaustu" -> "supernatural"
            "mystery", "gizem" -> "mystery"
            "horror", "korku" -> "horror"
            "sports", "spor" -> "sports"
            "slice of life", "yasamdan kesitler", "gunluk yasam" -> "slice-of-life"
            else -> null
        }
    }

    /**
     * Tümü aramasında bir sağlayıcıyı yalnızca bu filtreyi gerçekten uygulayabiliyorsa
     * çalıştır. Böylece desteklenmeyen etiket için yanlışlıkla filtresiz popüler sonuç
     * gösterilmez.
     */
    fun supportsDetailFilter(engine: SearchSourceEngine, request: DetailSearchFilterRequest): Boolean {
        if (engine == SearchSourceEngine.ALL) return true

        // Kimlik (ID) facetleri: stüdyo/yapımcı ve dergi kimlikleri yalnızca Jikan/MAL
        // arama uçlarında (`producers` / `magazines` parametreleri) uygulanabilir;
        // kanal kimlikleri ise TMDB'de. Uygulanamayan sağlayıcı filtresiz sonuç
        // döndürmesin diye diğer motorlar kapatılır.
        val hasMagazine = request.magazineId?.let { it > 0 } == true
        val hasProducerFacet = request.producerId?.let { it > 0 } == true ||
            request.studioId?.let { it > 0 } == true
        val hasNetworkFacet = request.networkId?.let { it > 0 } == true
        if (hasMagazine || hasProducerFacet) {
            return engine == SearchSourceEngine.MAL
        }
        if (hasNetworkFacet) {
            return engine == SearchSourceEngine.TMDB
        }

        val facetTerms = listOfNotNull(request.genre, request.theme, request.demographic)
            .map { SearchTranslation.translateToEnglishForSearch(it).trim() }
            .filter { it.isNotBlank() }
        val tag = request.tag?.let(SearchTranslation::translateToEnglishForSearch)?.trim()?.takeIf { it.isNotBlank() }
        val season = normalizeSeason(request.season)
        val hasYear = request.year?.let { it > 0 } == true
        if (facetTerms.isEmpty() && tag == null && season == null && !hasYear) return false

        val isManga = request.mediaType == MediaType.Manga
        val isTv = request.mediaType == MediaType.TvShow
        val termSupported = facetTerms.all { term ->
            when (engine) {
                SearchSourceEngine.ANILIST, SearchSourceEngine.KITSU, SearchSourceEngine.BANGUMI -> true
                SearchSourceEngine.MAL -> mapMalGenreId(term, isManga) != null
                SearchSourceEngine.TMDB -> mapTmdbGenreId(term, isTv) != null
                SearchSourceEngine.SHIKIMORI -> mapAnimeGenreId(term) != null || mapMalGenreId(term, isManga) != null
                SearchSourceEngine.SIMKL -> mapSimklGenre(term) != null
                SearchSourceEngine.ALL -> true
            }
        }
        val tagSupported = tag == null || when (engine) {
            SearchSourceEngine.ANILIST, SearchSourceEngine.KITSU, SearchSourceEngine.BANGUMI -> true
            SearchSourceEngine.MAL -> mapMalGenreId(tag, isManga) != null
            SearchSourceEngine.TMDB -> isTmdbTagSource(request.tagSource) && request.keywordId?.let { it > 0 } == true
            SearchSourceEngine.SHIKIMORI -> mapAnimeGenreId(tag) != null || mapMalGenreId(tag, isManga) != null
            SearchSourceEngine.SIMKL -> mapSimklGenre(tag) != null
            SearchSourceEngine.ALL -> true
        }
        if (!termSupported || !tagSupported) return false

        // A year-only facet is available more widely. A season is a stricter facet:
        // providers that can only accept a year must not return an entire year's catalog.
        if (season != null) {
            if (!hasYear || request.mediaType != MediaType.Anime) return false
            return when (engine) {
                SearchSourceEngine.ANILIST, SearchSourceEngine.MAL,
                SearchSourceEngine.SHIKIMORI, SearchSourceEngine.KITSU -> true
                SearchSourceEngine.TMDB, SearchSourceEngine.SIMKL, SearchSourceEngine.BANGUMI,
                SearchSourceEngine.ALL -> false
            }
        }
        if (!hasYear) return true
        return when (engine) {
            SearchSourceEngine.ANILIST -> request.mediaType == MediaType.Anime || isManga
            SearchSourceEngine.MAL -> request.mediaType == MediaType.Anime || isManga
            SearchSourceEngine.TMDB -> request.mediaType == MediaType.Movie || isTv
            SearchSourceEngine.SHIKIMORI -> request.mediaType == MediaType.Anime || isManga
            SearchSourceEngine.KITSU -> request.mediaType == MediaType.Anime || isManga
            SearchSourceEngine.SIMKL -> request.mediaType != MediaType.Manga
            // Bangumi `filter.air_date` süzgecini 三次元 (type = 6) kataloğunda da uygular
            // (probe A6/F4); dizi/film detayından yıl çipine basıldığında Bangumi
            // atlanıyordu, artık üç kapsamda da yıl süzgeci gönderilir.
            SearchSourceEngine.BANGUMI -> isTv || request.mediaType == MediaType.Anime ||
                isManga || request.mediaType == MediaType.Movie
            SearchSourceEngine.ALL -> true
        }
    }

    /**
     * Akıllı Kapsam Koruması (Auto-Scope Guard):
     * Seçilen kaynak ve medya türüne göre en uygun SearchScope'u belirler.
     */
    fun autoScopeGuard(engine: SearchSourceEngine, mediaType: MediaType): SearchScope {
        return when (engine) {
            SearchSourceEngine.ALL -> when (mediaType) {
                MediaType.Anime -> SearchScope.ANIME
                MediaType.Manga -> SearchScope.MANGA
                MediaType.Movie -> SearchScope.MOVIE
                MediaType.TvShow -> SearchScope.TV
            }
            SearchSourceEngine.ANILIST -> when (mediaType) {
                MediaType.Anime -> SearchScope.ANIME
                MediaType.Manga -> SearchScope.MANGA
                MediaType.Movie, MediaType.TvShow -> SearchScope.ANIME
            }
            SearchSourceEngine.MAL -> when (mediaType) {
                MediaType.Anime -> SearchScope.ANIME
                MediaType.Manga -> SearchScope.MANGA
                MediaType.Movie, MediaType.TvShow -> SearchScope.ANIME
            }
            SearchSourceEngine.TMDB -> when (mediaType) {
                MediaType.Movie -> SearchScope.MOVIE
                MediaType.TvShow -> SearchScope.TV
                MediaType.Anime -> SearchScope.ANIME
                MediaType.Manga -> SearchScope.MOVIE
            }
            SearchSourceEngine.SHIKIMORI -> when (mediaType) {
                MediaType.Anime -> SearchScope.ANIME
                MediaType.Manga -> SearchScope.MANGA
                MediaType.Movie, MediaType.TvShow -> SearchScope.ANIME
            }
            SearchSourceEngine.KITSU -> when (mediaType) {
                MediaType.Anime -> SearchScope.ANIME
                MediaType.Manga -> SearchScope.MANGA
                MediaType.Movie, MediaType.TvShow -> SearchScope.ANIME
            }
            SearchSourceEngine.SIMKL -> when (mediaType) {
                MediaType.Anime -> SearchScope.ANIME
                MediaType.TvShow -> SearchScope.TV
                MediaType.Movie -> SearchScope.MOVIE
                MediaType.Manga -> SearchScope.ANIME
            }
            SearchSourceEngine.BANGUMI -> when (mediaType) {
                MediaType.Anime -> SearchScope.ANIME
                MediaType.Manga -> SearchScope.MANGA
                // Bangumi'de film ayrı bir tür değil, 动画 içinde 剧场版 alt kategorisidir.
                MediaType.Movie, MediaType.TvShow -> SearchScope.ANIME
            }
        }
    }

    /**
     * MAL Anime vs Manga Genre ID Haritası
     */
    fun mapMalGenreId(genreOrTheme: String, isManga: Boolean): String? {
        val lower = genreOrTheme.lowercase().trim()
        return when (lower) {
            // Ortak Ana Türler
            "action", "aksiyon" -> "1"
            "adventure", "macera" -> "2"
            "avant garde" -> "5"
            "award winning", "ödüllü" -> "46"
            "boys love", "yaoi", "shounen ai" -> "28"
            "comedy", "komedi" -> "4"
            "drama", "dram" -> "8"
            "fantasy", "fantastik" -> "10"
            "girls love", "yuri", "shoujo ai" -> "26"
            "gourmet", "yemek" -> "47"
            "horror", "korku" -> "14"
            "mystery", "gizem" -> "7"
            "romance", "romantik" -> "22"
            "sci-fi", "bilim kurgu" -> "24"
            "slice of life", "günlük yaşam" -> "36"
            "sports", "spor" -> "30"
            "supernatural", "doğaüstü" -> "37"
            "space", "space opera" -> "29"
            "suspense", "gerilim" -> if (isManga) "45" else "41"
            "ecchi" -> "9"
            "erotica" -> "49"
            "hentai" -> "12"

            // Demografiler
            "shounen" -> "27"
            "shoujo" -> "25"
            "seinen" -> if (isManga) "41" else "42"
            "josei" -> if (isManga) "42" else "43"
            "kids", "çocuk" -> "15"

            // Popüler Temalar
            "isekai" -> "62"
            "reincarnation", "reenkarnasyon" -> if (isManga) "73" else "72"
            "time travel", "zaman yolculuğu" -> if (isManga) "79" else "78"
            "vampire", "vampir" -> "32"
            "gore" -> "58"
            "harem" -> "35"
            "reverse harem" -> if (isManga) "74" else "73"
            "psychological", "psikolojik" -> "40"
            "detective", "dedektif" -> "39"
            "military", "askeri" -> "38"
            "mecha" -> "18"
            "music", "müzik" -> "19"
            "school", "okul" -> "23"
            "historical", "tarihi" -> "13"
            "samurai", "samuray" -> "21"
            "martial arts", "dövüş sanatları" -> "17"
            "super power", "süper güç" -> "31"
            "survival", "hayatta kalma" -> if (isManga) "77" else "76"
            "crossdressing" -> if (isManga) "44" else "81"
            "urban fantasy" -> "82"
            "video game" -> if (isManga) "80" else "79"
            "villainess" -> if (isManga) "81" else "83"
            "workplace", "iş yeri" -> "48"
            else -> null
        }
    }

    /**
     * TMDB Movie vs TV Genre ID Haritası
     */
    fun mapTmdbGenreId(genre: String, isTv: Boolean): Int? {
        val lower = SearchTranslation.translateToEnglishForSearch(genre)
            .lowercase(java.util.Locale.ROOT).trim()
        return when (lower) {
            "action", "aksiyon" -> if (isTv) 10759 else 28
            "adventure", "macera" -> if (isTv) 10759 else 12
            "animation", "animasyon", "anime" -> 16
            "comedy", "komedi" -> 35
            "crime", "suc" -> 80
            "documentary", "belgesel" -> 99
            "drama", "dram" -> 18
            "family", "aile" -> 10751
            "fantasy", "fantastik" -> if (isTv) 10765 else 14
            "history", "tarih" -> if (isTv) null else 36
            "horror", "korku" -> if (isTv) null else 27
            "music", "muzik" -> 10402
            "mystery", "gizem" -> 9648
            "romance", "romantik" -> if (isTv) null else 10749
            "sci-fi", "science fiction", "bilim kurgu" -> if (isTv) 10765 else 878
            "tv movie", "tv filmi" -> if (isTv) null else 10770
            "thriller", "gerilim" -> if (isTv) null else 53
            "war", "savas" -> if (isTv) 10768 else 10752
            "western", "kovboy" -> if (isTv) null else 37
            "news" -> if (isTv) 10763 else null
            "reality" -> if (isTv) 10764 else null
            "talk show" -> if (isTv) 10767 else null
            "soap", "soap opera" -> if (isTv) 10766 else null
            "kids" -> if (isTv) 10762 else null
            else -> null
        }
    }
}
