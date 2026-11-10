package com.kitsugi.animelist.data.cloudstream

import android.util.Log
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import java.text.Normalizer
import java.util.Locale

/**
 * Unicode-aware title and season matching shared by every CloudStream provider.
 *
 * Matching is intentionally conservative: a provider returning search results is not
 * sufficient reason to load the first result. Names, aliases, year, season, and the
 * provider's declared media type all contribute to selecting a result.
 */
internal object CsTitleMatcher {

    private const val TAG = "CsTitleMatcher"
    private const val MIN_MATCH_SCORE = 0.45

    private val seasonMarkerPatterns = listOf(
        Regex(
            """(?<![\p{L}\p{N}])(\d{1,2})(?:st|nd|rd|th)?\.?[\s-]*(?:seasons?|sezon(?:u|lar)?|cour|part)\b""",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            """(?<![\p{L}\p{N}])(?:seasons?|sezon(?:u|lar)?|cour|part)\s*[:.-]?\s*(\d{1,2})\b""",
            RegexOption.IGNORE_CASE
        ),
        Regex("""(?<![\p{L}\p{N}])s\s*(\d{1,2})\b""", RegexOption.IGNORE_CASE),
        Regex("""第\s*([0-9]{1,2}|[〇零一二三四五六七八九十]+)\s*(?:期|季)"""),
        Regex("""(?<![0-9])([0-9]{1,2}|[〇零一二三四五六七八九十]+)\s*(?:期|季)"""),
        Regex("""第([〇零一二三四五六七八九十]+)(?:期|季)"""),
        Regex("""([〇零一二三四五六七八九十]+)(?:期|季)""")
    )

    private val romanSeasonPattern = Regex(
        """(?:^|[\s:：\-])(?:ix|viii|vii|vi|v|iv|iii|ii)(?=$|\s*[:：\-]|\s*[\[(])""",
        RegexOption.IGNORE_CASE
    )
    private val trailingSeasonNumberPattern = Regex("""(?:^|[\s:：\-])([2-9]|1[0-9]|2[0-5])\s*$""")
    private val parenthesizedYearPattern = Regex("""\(((?:19|20)\d{2})\)""")
    private val trailingYearPattern = Regex("""\s+((?:19|20)\d{2})\s*$""")
    private val romanValues = listOf(
        "x" to 10, "ix" to 9, "viii" to 8, "vii" to 7, "vi" to 6,
        "v" to 5, "iv" to 4, "iii" to 3, "ii" to 2
    )
    private val cjkDigitValues = mapOf(
        '〇' to 0, '零' to 0, '一' to 1, '二' to 2, '三' to 3, '四' to 4,
        '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9
    )
    private val genericWords = setOf(
        "the", "and", "of", "a", "an", "to", "in", "on", "for", "from", "with",
        "season", "seasons", "sezon", "sezonu", "sezonlar", "part", "cour", "episode", "ep", "bölüm", "bolum",
        "movie", "film", "series", "dizi", "anime", "izle", "watch", "full", "hd", "turkce",
        "dublaj", "altyazi", "subtitle", "subbed", "dubbed",
        // Turkish streaming site common suffixes/prefixes (noise words)
        "fragman", "fragmani", "fragmanı", "orijinal", "orginal", "yerli", "yabanci", "yabanci",
        "tum", "tumbolumler", "tumbolum", "tumsezonlar", "tumsezon", "hepsi", "tumu",
        "bedava", "ucretsiz", "ucretsizizle", "online", "indir", "download", "mp4", "m3u8",
        "yuksek", "kalite", "kaliteli", "yeni", "son", "eklenen", "2024", "2025", "2026",
        "sadece", "burada", "sitemizde", "izle2", "izle3", "izle4", "izle5", "izle6",
        "cizgifilm", "cizgi", "animeizle", "animeizle2", "animeizle3",
        "altyazili", "altyazılı", "dublajli", "dublajlı", "sesli", "turkcedublaj", "turkcealtyazi",
        "cokdilli", "çokdilli", "multidublaj", "multi", "dual", "orjinaldil", "orijinaldil",
        "ses", "audio", "video", "bolumizle", "bolumizle2", "sezonizle", "filmizle", "diziizle",
        "1080p", "720p", "480p", "360p", "4k", "uhd", "fhd", "hq", "lq", "sd",
        "webrip", "webdl", "bluray", "bdrip", "hdrip", "dvdrip", "hdcam", "cam",
        "tarz", "turu", "tur", "yapim", "yapimi", "yayin", "yayini",
        "kanal", "kanali", "canli", "canlı", "tv", "televizyon"
    )

    /**
     * Turkish streaming sites append common noise patterns to titles.
     * These patterns are stripped from search result names before matching
     * to improve match accuracy against the canonical title.
     *
     * Examples:
     *   "Spider-Man Örümcek-Adam izle" → "Spider-Man Örümcek-Adam"
     *   "Naruto Türkçe Dublaj 1080p" → "Naruto"
     *   "Dizi Full HD Altyazılı" → "Dizi"
     */
    private val turkishSiteNoisePatterns = listOf(
        // Quality suffixes: "1080p", "720p Full HD", "4K Ultra HD"
        Regex("""\s+(?:\d{3,4}p|4k|uhd|fhd|hd|hq|lq|sd)\b.*$""", RegexOption.IGNORE_CASE),
        Regex("""\s+(?:full\s+)?(?:hd|hq|lq|sd|4k|uhd|fhd)\b.*$""", RegexOption.IGNORE_CASE),
        // "izle" variants at end: "izle", "izle 2", "izle3", "film izle", "dizi izle"
        Regex("""\s+(?:film|dizi|anime|video|bolum|bölüm|sezon)?\s*izle\s*\d*\s*$""", RegexOption.IGNORE_CASE),
        Regex("""\s+izle\s*\d*\s*$""", RegexOption.IGNORE_CASE),
        // "Türkçe Dublaj" / "Türkçe Altyazı" / "Altyazılı" / "Dublajlı"
        Regex("""\s+(?:türkçe|turkce|turkish)\s+(?:dublaj|altyazı|altyazi|ses|audio)\b.*$""", RegexOption.IGNORE_CASE),
        Regex("""\s+(?:dublaj|altyazı|altyazi|dublajlı|altyazılı|sesli)\b.*$""", RegexOption.IGNORE_CASE),
        Regex("""\s+(?:türkçe|turkce|turkish)\b.*$""", RegexOption.IGNORE_CASE),
        // "Full HD" / "Full" at end
        Regex("""\s+full\b.*$""", RegexOption.IGNORE_CASE),
        // "Online izle" / "Ücretsiz izle" / "Bedava izle"
        Regex("""\s+(?:online|ücretsiz|ucretsiz|bedava|ücretsizizle|ucretsizizle)\s+(?:izle\s*)?$""", RegexOption.IGNORE_CASE),
        // "Türkçe" alone at end
        Regex("""\s+(?:türkçe|turkce)\s*$""", RegexOption.IGNORE_CASE),
        // Year in title: "2024", "2025"
        Regex("""\s+(?:19|20)\d{2}\s*$"""),
        // "Yeni" / "Son" at end
        Regex("""\s+(?:yeni|son|eklenen)\s*$""", RegexOption.IGNORE_CASE),
        // Codec/format noise: "x264", "x265", "HEVC", "WEB-DL", "BluRay"
        Regex("""\s+(?:x264|x265|h264|h265|hevc|avc|web-?dl|webrip|bluray|bdrip|hdrip|dvdrip)\b.*$""", RegexOption.IGNORE_CASE),
        // "Canlı" / "TV" suffix
        Regex("""\s+(?:canlı|canli|tv|kanal)\s*$""", RegexOption.IGNORE_CASE),
        // "İzle" with site name: "Siteadi izle"
        Regex("""\s+\w+\s+izle\s*$""", RegexOption.IGNORE_CASE),
        // Multiple spaces cleanup will happen after
    )

    /**
     * Strips common Turkish streaming site noise from a search result title.
     * This improves matching when the provider returns titles like
     * "Naruto Shippuden Türkçe Dublaj 1080p izle" for a search of "Naruto Shippuden".
     */
    fun stripTurkishSiteNoise(rawTitle: String): String {
        if (rawTitle.isBlank()) return rawTitle
        var clean = Normalizer.normalize(rawTitle, Normalizer.Form.NFKC).trim()
        // Apply noise patterns iteratively (some may reveal more noise after removal)
        var previous: String
        var iterations = 0
        do {
            previous = clean
            for (pattern in turkishSiteNoisePatterns) {
                clean = clean.replace(pattern, " ")
            }
            clean = clean.replace(Regex("\\s+"), " ").trim()
            iterations++
        } while (clean != previous && iterations < 3)
        return clean.trim(' ', '-', '–', '—', ':', '：')
    }

    /**
     * Extracts only an explicitly marked season from a title. Unlike [parseSeasonFromTitle],
     * this never treats a trailing number as a season, which matters for episode labels such as
     * "Episode 2".
     */
    fun parseExplicitSeasonFromTitle(title: String): Int? {
        val normalized = Normalizer.normalize(title, Normalizer.Form.NFKC).lowercase(Locale.ROOT).trim()
        parseMarkedSeason(normalized)?.let { return it }

        // Common episode-label form: "2x05". The trailing episode number is not a season.
        Regex("""(?<![0-9])(\d{1,2})\s*[x×]\s*\d{1,3}(?![0-9])""", RegexOption.IGNORE_CASE)
            .find(normalized)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }

        val romanMatch = romanSeasonPattern.find(normalized) ?: return null
        val roman = romanMatch.value.trim().trimStart(':', '：', '-', '(', '[').lowercase(Locale.ROOT)
        return romanValues.firstOrNull { (token, _) -> roman == token }?.second
    }

    /**
     * Extracts a season/part number in common English, Turkish, Japanese, and Chinese formats.
     * A plain trailing number is accepted for common sequel-style anime titles (e.g. "Overlord 4").
     */
    fun parseSeasonFromTitle(title: String): Int? {
        val normalized = Normalizer.normalize(title, Normalizer.Form.NFKC).lowercase(Locale.ROOT).trim()
        parseExplicitSeasonFromTitle(normalized)?.let { return it }

        // Only a short, terminal number is treated as a sequel season; years are never accepted.
        val trailing = trailingSeasonNumberPattern.find(normalized)?.groupValues?.getOrNull(1)?.toIntOrNull()
        return trailing?.takeIf { it in 2..25 }
    }

    /**
     * Removes only known season/year qualifiers. Subtitle/franchise text after ':' or '-'
     * stays intact (e.g. "Naruto: Shippuden" must not collapse to "Naruto").
     */
    fun extractCleanBaseTitle(rawTitle: String, preserveInstallment: Boolean = false): String {
        var clean = Normalizer.normalize(rawTitle, Normalizer.Form.NFKC).trim()
        clean = clean.replace(Regex("\\s*\\((?:19|20)\\d{2}\\)"), " ")
        // Strip a trailing release year, but keep meaningful leading/embedded numbers such as 2001.
        clean = clean.replace(Regex("\\s+(?:19|20)\\d{2}\\s*$"), " ")

        if (!preserveInstallment) {
            seasonMarkerPatterns.forEach { pattern -> clean = clean.replace(pattern, " ") }
            if (parseSeasonFromTitle(rawTitle) != null) {
                clean = clean.replace(romanSeasonPattern, " ")
                clean = clean.replace(trailingSeasonNumberPattern, " ")
            }
        }

        return clean.replace(Regex("\\s+"), " ")
            .trim()
            .replace(Regex("\\s+([:：])"), "\$1")
            .trim(':', '：', '-', '–', '—')
    }

    /**
     * Builds full-title query variants first (all supplied language aliases), then safe
     * normalization variants and season-specific queries. Broad one-word fallbacks are omitted
     * so a common word cannot make a provider return an unrelated title as a false match.
     */
    fun buildTitleVariants(
        main: String,
        alts: List<String>,
        season: Int? = null,
        isMovie: Boolean = false
    ): List<String> {
        val seasonVariants =
            if (!isMovie && season != null && season > 1) buildSeasonScopedVariants(main, alts, season)
            else emptyList()
        return (buildPlainTitleVariants(main, alts, isMovie) + seasonVariants).distinct()
    }

    /**
     * YALNIZCA BAŞLIK içeren arama varyantları — sezon/bölüm bilgisi ASLA karışmaz.
     *
     * Temel ilke: hiçbir site (Türkçe veya yabancı) "X 1. Sezon 1. Bölüm" tarzı sorguyu
     * anlayamaz; arama her zaman çıplak eser adıyla yapılır, sezon/bölüm bilgisi ancak
     * içerik sayfasına girildikten SONRA bölüm listesinden eşleştirme için kullanılır.
     *
     * Language aliases lead the query list; cap raw synonyms so provider requests remain bounded.
     * Include ALL alternative titles (English, Romaji, Japanese, Chinese, Turkish, synonyms)
     * — Turkish streaming sites often use Turkish-translated titles that only appear in synonyms.
     */
    fun buildPlainTitleVariants(
        main: String,
        alts: List<String>,
        isMovie: Boolean = false
    ): List<String> {
        val variants = linkedSetOf<String>()
        val fullTitles = (listOf(main) + alts)
            .map { Normalizer.normalize(it, Normalizer.Form.NFKC).trim() }
            .filter { normalizeTitleForMatch(it).length >= 2 }
            .distinctBy(::queryKey)
            .take(12)  // Increased from 8 to 12 to include more language variants

        fun addVariant(value: String?) {
            val candidate = value?.trim().orEmpty()
            if (normalizeTitleForMatch(candidate).length >= 2) variants.add(candidate)
        }

        fullTitles.forEach(::addVariant)
        val cleanedBases = fullTitles.map { extractCleanBaseTitle(it, preserveInstallment = isMovie) }
            .filter { normalizeTitleForMatch(it).length >= 2 }
            .distinctBy(::queryKey)

        (fullTitles + cleanedBases).forEach { base ->
            addVariant(simplifyTitle(base))
            addVariant(toAsciiTitle(base))
            addVariant(normalizeTitleForMatch(base))
            // Turkish sites often don't handle Turkish characters well in search.
            // Add a fully ASCII-transliterated variant (ğ→g, ş→s, ı→i, etc.)
            val asciiBase = toAsciiTitle(base)
            if (asciiBase != base) addVariant(asciiBase)
        }

        return variants.toList()
    }

    /**
     * Sezon-kapsamlı varyantlar ("X 2. Sezon", "X Season 2", ...) — ASLA birincil arama
     * sorgusu olarak kullanılmaz. İki güvenlik ağı durumunda devreye girer:
     *  1. Çıplak başlık araması hiçbir sonuç döndürmediğinde (bazı siteler sezonları
     *     ayrı girdiler olarak indeksler; "X 2. Sezon" sitenin kendi girdi adıdır).
     *  2. İçerik sayfası yüklendi ama hedef sezon o sayfada yoksa — sezon sayfasına
     *     gezinmenin genel (kaynak-bağımsız) yolu eklentinin kendi aramasını kullanmaktır.
     */
    fun buildSeasonScopedVariants(
        main: String,
        alts: List<String>,
        season: Int
    ): List<String> {
        if (season <= 1) return emptyList()
        val variants = linkedSetOf<String>()

        fun addVariant(value: String?) {
            val candidate = value?.trim().orEmpty()
            if (normalizeTitleForMatch(candidate).length >= 2) variants.add(candidate)
        }

        val fullTitles = (listOf(main) + alts)
            .map { Normalizer.normalize(it, Normalizer.Form.NFKC).trim() }
            .filter { normalizeTitleForMatch(it).length >= 2 }
            .distinctBy(::queryKey)
            .take(12)
        val cleanedBases = fullTitles.map { extractCleanBaseTitle(it, preserveInstallment = false) }
            .filter { normalizeTitleForMatch(it).length >= 2 }
            .distinctBy(::queryKey)

        val primaryBases = (listOfNotNull(cleanedBases.firstOrNull()) + cleanedBases.drop(1).take(3))
            .distinctBy(::queryKey)
        val suffixes = buildList {
            add("$season. Sezon")
            add("Season $season")
            add("S$season")
            add("$season. sezon")
            add(season.toString())
            romanValues.firstOrNull { it.second == season }?.let { add(it.first.uppercase(Locale.ROOT)) }
            val cjk = toCjkNumber(season)
            add("第${season}期")
            add("第${cjk}期")
            add("第${season}季")
            add("第${cjk}季")
        }
        for (base in primaryBases) {
            for (suffix in suffixes) addVariant("$base $suffix")
        }

        return variants.toList()
    }

    /**
     * Selects the best semantically compatible provider result. Low-confidence results are
     * rejected rather than silently returning the provider's first (often unrelated) result.
     */
    fun findBestMatch(
        results: List<SearchResponse>,
        mainTitle: String,
        altTitles: List<String>,
        targetYear: Int?,
        targetSeason: Int? = null,
        targetEpisode: Int? = null,
        isMovie: Boolean? = null
    ): SearchResponse? {
        var bestScore = Double.NEGATIVE_INFINITY
        var bestMatch: SearchResponse? = null
        val queryTitles = (listOf(mainTitle) + altTitles)
            .map { Normalizer.normalize(it, Normalizer.Form.NFKC).trim() }
            .filter { normalizeTitleForMatch(it).length >= 2 }
            .distinctBy(::queryKey)
        if (queryTitles.isEmpty()) return null

        val querySeasons = queryTitles.mapNotNull(::parseSeasonFromTitle).toSet()
        val expectedSeason = when {
            targetSeason != null && targetSeason > 1 -> targetSeason
            querySeasons.isNotEmpty() -> querySeasons.maxOrNull()
            else -> targetSeason
        }
        val expectedMovieInstallment = queryTitles.mapNotNull(::parseMovieInstallment).firstOrNull()

        fun normalizedForms(title: String): List<String> = buildList {
            add(normalizeTitleForMatch(title))
            val clean = extractCleanBaseTitle(title, preserveInstallment = isMovie == true)
            if (clean != normalizeTitleForMatch(title)) add(normalizeTitleForMatch(clean))
        }.filter { it.isNotBlank() }

        val normalizedQueries = queryTitles.flatMap(::normalizedForms).distinct()
        val queryWordSets = queryTitles.map { title ->
            significantWords(normalizeTitleForMatch(title))
        }.filter { it.isNotEmpty() }
        val strictAliasWordSets = queryWordSets.filter { it.size >= 2 }
        val strictNormalizedQueries = queryTitles
            .filter { significantWords(normalizeTitleForMatch(it)).size >= 2 }
            .flatMap(::normalizedForms)
            .distinct()
        val unicodeAliasQueries = queryTitles
            .filter { title ->
                val normalized = normalizeTitleForMatch(title)
                normalized.any { it.code > 0x7F } && significantWords(normalized).size == 1
            }
            .flatMap(::normalizedForms)
            .distinct()

        for (result in results) {
            // Live/Music/etc. are never playable video — always drop.
            // Movie↔TvSeries mismatch is deferred until after similarity: TR plugins
            // frequently tag every result as Movie (or every result as TvSeries).
            // A 1.00 title hit used to be thrown away here ("isim gitti, devamı gelmedi").
            if (isNonVideoType(result.type)) {
                Log.d(TAG, "  -> Non-video type, rejecting '${result.name}' type=${result.type}")
                continue
            }

            val resultName = Normalizer.normalize(result.name, Normalizer.Form.NFKC).trim()
            // Strip Turkish streaming site noise (izle, full, hd, türkçe dublaj, 1080p, etc.)
            // before matching — Turkish providers often append these to titles.
            val resultNameCleaned = stripTurkishSiteNoise(resultName)
            val resultYear = getResultYear(result) ?: parseYearFromTitle(resultName)
            if (isMovie == true && targetYear != null && resultYear != null && kotlin.math.abs(resultYear - targetYear) > 5) {
                Log.d(TAG, "  -> Movie year mismatch, rejecting '${result.name}' expected=$targetYear found=$resultYear")
                continue
            }
            val resultSeason = if (isMovie == true) null else parseSeasonFromTitle(resultName) ?: parseSeasonFromSlug(result.url)
            if (expectedSeason != null && resultSeason != null && resultSeason != expectedSeason) {
                Log.d(TAG, "  -> Season mismatch, rejecting '${result.name}' expected=$expectedSeason found=$resultSeason")
                continue
            }
            val resultEpisode = if (isMovie == true) null else parseEpisodeFromTitle(resultName)
            if (targetEpisode != null && resultEpisode != null && resultEpisode != targetEpisode) {
                Log.d(TAG, "  -> Episode mismatch, rejecting '${result.name}' expected=$targetEpisode found=$resultEpisode")
                continue
            }
            val resultInstallment = if (isMovie == true) movieInstallmentOf(resultName) else null
            if (isMovie == true && expectedMovieInstallment != null) {
                val installmentMismatch = resultInstallment != null && resultInstallment != expectedMovieInstallment
                val missingInstallmentWithoutYearProof = resultInstallment == null &&
                    (targetYear == null || resultYear != targetYear)
                if (installmentMismatch || missingInstallmentWithoutYearProof) {
                    Log.d(TAG, "  -> Movie installment mismatch/unknown, rejecting '${result.name}' expected=$expectedMovieInstallment found=$resultInstallment year=$resultYear")
                    continue
                }
            }
            // Ters yön: sorgu numarasız ("Avatar") ama aday numaralı ("Avatar 2"). Yıl hedefle
            // tam eşleşmiyorsa bu bir devam filmidir; yalnızca devam filmi varsa yanlış film oynatılmasın.
            if (isMovie == true && expectedMovieInstallment == null && resultInstallment != null &&
                !(targetYear != null && resultYear == targetYear)
            ) {
                Log.d(TAG, "  -> Sequel-numbered candidate for un-numbered movie query, rejecting '${result.name}' found=$resultInstallment year=$resultYear")
                continue
            }
            val resultForms = linkedSetOf(
                normalizeTitleForMatch(resultName),
                normalizeTitleForMatch(resultNameCleaned),
                normalizeTitleForMatch(extractCleanBaseTitle(resultName, preserveInstallment = isMovie == true)),
                normalizeTitleForMatch(extractCleanBaseTitle(resultNameCleaned, preserveInstallment = isMovie == true))
            )
            resultForms.removeAll { it.isBlank() }

            var titleSimilarity = 0.0
            var strictTitleSimilarity = 0.0
            var unicodeAliasSimilarity = 0.0
            for (candidate in resultForms) {
                for (query in normalizedQueries) {
                    titleSimilarity = maxOf(titleSimilarity, getSimilarity(candidate, query))
                }
                for (query in strictNormalizedQueries) {
                    strictTitleSimilarity = maxOf(strictTitleSimilarity, getSimilarity(candidate, query))
                }
                for (query in unicodeAliasQueries) {
                    unicodeAliasSimilarity = maxOf(unicodeAliasSimilarity, getSimilarity(candidate, query))
                }
            }

            val resultWords = resultForms.flatMapTo(mutableSetOf()) { significantWords(it) }
            var overlapBonus = 0.0
            var bestWordCoverage = 0.0
            for (queryWords in queryWordSets) {
                if (queryWords.isNotEmpty()) {
                    val shared = queryWords.intersect(resultWords).size
                    val unionSize = (queryWords + resultWords).size
                    val jaccard = if (unionSize > 0) shared.toDouble() / unionSize else 0.0
                    val queryCoverage = shared.toDouble() / queryWords.size
                    if (queryWords.size >= 2) bestWordCoverage = maxOf(bestWordCoverage, queryCoverage)
                    overlapBonus = maxOf(overlapBonus, jaccard * 0.25, queryCoverage * 0.12)
                }
            }

            // A result that only matches the first word of a longer franchise title is usually
            // the wrong season/series (e.g. Naruto vs Naruto Shippuden). Require either a full
            // alias-token match or near-exact spelling before considering it.
            //
            // v2.4.249 (CS-TANI-2026-10-11): never reject a candidate that near-exactly equals ANY
            // known title. Multi-word mixed-script aliases (e.g. 'SUPERNATURAL スーパーナチュラル'
            // from AniList/TMDB synonyms) used to activate this filter and reject perfect 1.00
            // Latin matches ('Supernatural') on Turkish providers (DiziMom/DiziYou/Dizilla/Movix
            // EŞLEŞME YOK with score 1.00). An exact title hit is by definition correct; the
            // franchise guard below still rejects partial hits (Naruto=0.38 vs Shippuden).
            if (strictAliasWordSets.isNotEmpty() && bestWordCoverage < 0.75 &&
                strictTitleSimilarity < 0.90 && unicodeAliasSimilarity < 0.90 &&
                titleSimilarity < 0.90
            ) {
                Log.d(TAG, "  -> Too few distinctive title tokens match '${result.name}'")
                continue
            }

            val typeMismatch = !isTypeCompatible(result.type, isMovie)
            if (typeMismatch && titleSimilarity < 0.85) {
                Log.d(TAG, "  -> Media type mismatch, rejecting '${result.name}' type=${result.type} movie=$isMovie sim=${"%.2f".format(titleSimilarity)}")
                continue
            }

            var score = titleSimilarity + overlapBonus
            if (typeMismatch) {
                // Prefer a correctly-typed sibling when both exist (The Silent Voice
                // movie vs series). A lone mis-tagged exact title still survives.
                score -= 0.20
                Log.d(TAG, "  -> Type mismatch kept for strong title '${result.name}' type=${result.type} movie=$isMovie")
            }
            if (targetYear != null && resultYear != null) {
                val yearDelta = kotlin.math.abs(resultYear - targetYear)
                score += when {
                    yearDelta == 0 -> 0.15
                    yearDelta == 1 -> 0.05
                    yearDelta <= 3 -> -0.15
                    else -> -0.25
                }
            }

            if (targetEpisode != null && isMovie != true && resultEpisode != null) {
                score += 0.30
            }

            if (expectedSeason != null && isMovie != true) {
                if (resultSeason != null) {
                    score += 0.35
                } else if (expectedSeason <= 1) {
                    score += 0.10
                } else {
                    // Some Turkish providers expose one series container with all seasons.
                    score -= 0.15
                }
            }

            if (isMovie == true) {
                when {
                    expectedMovieInstallment != null && resultInstallment == expectedMovieInstallment -> score += 0.15
                    expectedMovieInstallment != null && resultInstallment == null && targetYear != null && resultYear != targetYear -> score -= 0.15
                }
            }

            Log.d(TAG, "  Candidate: '${result.name}' title=${"%.2f".format(titleSimilarity)} total=${"%.2f".format(score)}")
            if (score > bestScore) {
                bestScore = score
                bestMatch = result
            }
        }

        Log.d(TAG, "Best candidate score=${"%.2f".format(bestScore)} (threshold=$MIN_MATCH_SCORE)")
        return bestMatch?.takeIf { bestScore >= MIN_MATCH_SCORE }
    }

    /** Rejects provider results whose available metadata conflicts with the requested media. */
    fun isCandidateMetadataCompatible(
        result: SearchResponse,
        mainTitle: String,
        altTitles: List<String>,
        targetYear: Int?,
        targetSeason: Int?,
        targetEpisode: Int?,
        isMovie: Boolean?
    ): Boolean {
        if (!isTypeCompatible(result.type, isMovie)) return false
        val queryTitles = (listOf(mainTitle) + altTitles).distinctBy(::queryKey)
        val expectedSeason = when {
            targetSeason != null && targetSeason > 1 -> targetSeason
            else -> queryTitles.mapNotNull(::parseSeasonFromTitle).maxOrNull() ?: targetSeason
        }
        val expectedInstallment = queryTitles.mapNotNull(::parseMovieInstallment).firstOrNull()
        val resultYear = getResultYear(result) ?: parseYearFromTitle(result.name)
        if (isMovie == true && targetYear != null && resultYear != null && kotlin.math.abs(resultYear - targetYear) > 5) {
            return false
        }
        if (isMovie != true && expectedSeason != null) {
            val actualSeason = parseSeasonFromTitle(result.name) ?: parseSeasonFromSlug(result.url)
            if (actualSeason != null && actualSeason != expectedSeason) return false
        }
        if (isMovie != true && targetEpisode != null) {
            val actualEpisode = parseEpisodeFromTitle(result.name)
            if (actualEpisode != null && actualEpisode != targetEpisode) return false
        }
        if (isMovie == true && expectedInstallment != null) {
            val actualInstallment = movieInstallmentOf(result.name)
            if (actualInstallment != null && actualInstallment != expectedInstallment) return false
            if (actualInstallment == null && (targetYear == null || resultYear != targetYear)) return false
        }
        if (isMovie == true && expectedInstallment == null && movieInstallmentOf(result.name) != null &&
            !(targetYear != null && resultYear == targetYear)
        ) return false
        return true
    }

    /**
     * Returns true only when the name is near-exact and no available type/year/season metadata
     * contradicts it. Used for the fast path that skips extra provider ID validation.
     */
    fun isHighConfidenceMatch(
        result: SearchResponse,
        mainTitle: String,
        altTitles: List<String>,
        targetYear: Int?,
        targetSeason: Int?,
        isMovie: Boolean?
    ): Boolean {
        if (!isTypeCompatible(result.type, isMovie)) return false
        // Use enhanced similarity that strips Turkish site noise (izle, full, hd, türkçe dublaj, etc.)
        if (getBestTitleSimilarity(result.name, mainTitle, altTitles, isMovie) < 0.90) return false

        val resultYear = getResultYear(result) ?: parseYearFromTitle(result.name)
        if (targetYear != null && resultYear != null && kotlin.math.abs(resultYear - targetYear) > 1) return false

        if (isMovie == true) {
            val expected = (listOf(mainTitle) + altTitles).mapNotNull(::parseMovieInstallment).firstOrNull()
            val actual = movieInstallmentOf(result.name)
            if (expected == null && actual != null) return false
            if (expected != null && actual != null && expected != actual) return false
            if (expected != null && actual == null && (targetYear == null || resultYear != targetYear)) return false
        } else if (targetSeason != null) {
            val querySeason = (listOf(mainTitle) + altTitles).mapNotNull(::parseSeasonFromTitle).maxOrNull()
            val expected = if (targetSeason > 1) targetSeason else querySeason ?: targetSeason
            val actual = parseSeasonFromTitle(result.name) ?: parseSeasonFromSlug(result.url)
            if (actual != null && actual != expected) return false
            if (expected > 1 && actual == null) return false
        }
        return true
    }

    /** Highest Unicode-aware title similarity across the supplied canonical aliases. */
    fun getBestTitleSimilarity(
        candidateName: String,
        mainTitle: String,
        altTitles: List<String>,
        isMovie: Boolean? = null
    ): Double {
        val candidate = normalizeTitleForMatch(candidateName)
        if (candidate.isBlank()) return 0.0

        val candidateForms = linkedSetOf(candidate)
        // Strip Turkish streaming site noise from candidate (izle, full, hd, türkçe dublaj, 1080p, etc.)
        val candidateCleaned = stripTurkishSiteNoise(candidateName)
        if (candidateCleaned.isNotBlank() && candidateCleaned != candidateName) {
            candidateForms.add(normalizeTitleForMatch(candidateCleaned))
            if (isMovie != true) {
                candidateForms.add(normalizeTitleForMatch(extractCleanBaseTitle(candidateCleaned)))
            }
        }
        if (isMovie != true) {
            candidateForms.add(normalizeTitleForMatch(extractCleanBaseTitle(candidateName)))
        }
        val queries = (listOf(mainTitle) + altTitles)
            .flatMap { title ->
                listOf(
                    normalizeTitleForMatch(title),
                    normalizeTitleForMatch(extractCleanBaseTitle(title, preserveInstallment = isMovie == true))
                )
            }
            .filter { it.isNotBlank() }
            .distinct()
        if (queries.isEmpty()) return 0.0
        return candidateForms.filter { it.isNotBlank() }
            .maxOfOrNull { candidateForm -> queries.maxOf { query -> getSimilarity(candidateForm, query) } }
            ?: 0.0
    }

    /** Unicode NFKC + punctuation folding, preserving Japanese, Chinese, and other scripts. */
    fun normalizeTitleForMatch(title: String): String {
        if (title.isBlank()) return ""
        var normalized = Normalizer.normalize(title, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            // Locale.ROOT lowercases capital dotted İ to `i` + U+0307. Keeping that mark
            // turns it into a word boundary below, splitting "İstanbul" into "i stanbul".
            .replace("\u0307", "")
        // Treat dotted/dotless Turkish i and common Turkish characters like their ASCII spellings.
        normalized = normalized
            .replace('ı', 'i')
            .replace('ç', 'c')
            .replace('ğ', 'g')
            .replace('ö', 'o')
            .replace('ş', 's')
            .replace('ü', 'u')
        return normalized
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /** Removes punctuation and whitespace while retaining Turkish letters, for legacy callers. */
    fun simplifyTitle(title: String): String {
        return title
            .replace(Regex("[^a-zA-Z0-9\\sçÇğĞıİöÖşŞüÜ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /** Turkish transliteration retained for provider search endpoints that require ASCII. */
    fun toAsciiTitle(title: String): String {
        return title
            .replace("ç", "c").replace("Ç", "C")
            .replace("ğ", "g").replace("Ğ", "G")
            .replace("ı", "i").replace("İ", "I")
            .replace("ö", "o").replace("Ö", "O")
            .replace("ş", "s").replace("Ş", "S")
            .replace("ü", "u").replace("Ü", "U")
            .replace(Regex("[^a-zA-Z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /** Levenshtein similarity (0.0–1.0). Callers should normalize first. */
    fun getSimilarity(s1: String, s2: String): Double {
        val len1 = s1.length
        val len2 = s2.length
        if (len1 == 0 && len2 == 0) return 1.0
        if (len1 == 0 || len2 == 0) return 0.0

        // Two rows are sufficient and keep memory bounded for long source titles.
        var previous = IntArray(len2 + 1) { it }
        var current = IntArray(len2 + 1)
        for (i in 1..len1) {
            current[0] = i
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
            }
            val swap = previous
            previous = current
            current = swap
        }
        val maxLen = maxOf(len1, len2)
        return (maxLen - previous[len2]).toDouble() / maxLen
    }

    /**
     * Extracts a season number from provider URL path formats such as `2-sezon`, `season-3`,
     * `s2`, or a terminal `-2`. Bare terminal numbers over 10 are intentionally not trusted.
     */
    fun parseSeasonFromSlug(url: String): Int? {
        val path = try {
            java.net.URI(url).rawPath.orEmpty()
        } catch (_: Exception) {
            url.substringBefore('?').substringAfter("//", url).substringAfter('/', "")
        }
        val decoded = runCatching { java.net.URLDecoder.decode(path, Charsets.UTF_8.name()) }.getOrDefault(path)
        val slugTitle = decoded.replace(Regex("[-_/]+"), " ").trim()
        parseMarkedSeason(slugTitle)?.let { return it }
        if (romanSeasonPattern.containsMatchIn(slugTitle)) {
            parseSeasonFromTitle(slugTitle)?.let { return it }
        }

        val trailing = Regex("""(?:^|[-/])(\d{1,2})/?$""")
            .find(path.lowercase(Locale.ROOT))
            ?.groupValues?.getOrNull(1)
            ?.toIntOrNull()
        return trailing?.takeIf { it in 2..10 }
    }

    private fun parseMarkedSeason(value: String): Int? {
        for (pattern in seasonMarkerPatterns) {
            val match = pattern.find(value) ?: continue
            val numberText = match.groupValues.drop(1).firstOrNull { it.isNotBlank() } ?: continue
            val number = parseCjkNumber(numberText) ?: numberText.toIntOrNull()
            if (number != null && number in 1..25) return number
        }
        return null
    }

    private fun parseCjkNumber(token: String): Int? {
        token.toIntOrNull()?.let { return it }
        if (token.isBlank() || token.any { it !in cjkDigitValues && it != '十' }) return null
        if (token == "十") return 10

        val tenIndex = token.indexOf('十')
        if (tenIndex < 0) {
            var number = 0
            for (char in token) {
                number = number * 10 + (cjkDigitValues[char] ?: return null)
            }
            return number
        }
        val tens = if (tenIndex == 0) 1 else cjkDigitValues[token[tenIndex - 1]] ?: return null
        val ones = if (tenIndex == token.lastIndex) 0 else cjkDigitValues[token[tenIndex + 1]] ?: return null
        return tens * 10 + ones
    }

    private fun toCjkNumber(number: Int): String {
        val digits = listOf("〇", "一", "二", "三", "四", "五", "六", "七", "八", "九")
        return when {
            number < 10 -> digits[number]
            number == 10 -> "十"
            number < 20 -> "十${digits[number % 10].takeIf { number % 10 > 0 }.orEmpty()}"
            number < 30 -> "二十${digits[number % 10].takeIf { number % 10 > 0 }.orEmpty()}"
            else -> number.toString()
        }
    }

    private fun significantWords(normalized: String): Set<String> = normalized
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() && it !in genericWords && (it.length >= 2 || it.any { char -> char.code > 0x7F }) }
        .toSet()

    private fun queryKey(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    internal fun parseEpisodeFromTitle(title: String): Int? {
        val normalized = Normalizer.normalize(title, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
        val structuredPatterns = listOf(
            Regex("""(?<![\p{L}\p{N}])s\s*\d{1,2}[\s._-]*e\s*0*(\d{1,3})(?![0-9])"""),
            Regex("""(?<![0-9])\d{1,2}\s*x\s*0*(\d{1,3})(?![0-9])"""),
            Regex("""第\s*([0-9]{1,3}|[〇零一二三四五六七八九十]+)\s*(?:話|话|集)""")
        )
        for (pattern in structuredPatterns) {
            val numberText = pattern.find(normalized)?.groupValues?.getOrNull(1) ?: continue
            val number = numberText.toIntOrNull() ?: parseCjkNumber(numberText)
            if (number != null && number > 0) return number
        }

        val match = Regex(
            """(?:([0-9]{1,3})\s*(?:\.?\s*(?:bölüm|bolum|ep|episode|b\b))|\b(?:bölüm|bolum|ep|episode|b\b)\s*\.?\s*([0-9]{1,3}))""",
            RegexOption.IGNORE_CASE
        ).find(normalized) ?: return null
        return match.groupValues.drop(1).firstNotNullOfOrNull { it.toIntOrNull() }
    }

    /**
     * Film devam numarası: ham başlıkta bulunamazsa site gürültüsü temizlenmiş başlıkta aranır.
     * "Avatar 2 izle" gibi adlarda numara sonda değil, "izle" ekinden önce durur.
     */
    private fun movieInstallmentOf(title: String): Int? =
        parseMovieInstallment(title) ?: parseMovieInstallment(stripTurkishSiteNoise(title))

    private fun parseMovieInstallment(title: String): Int? {
        val normalized = normalizeTitleForMatch(title)
        val marker = Regex("""\b(?:part|chapter|movie|film)\s+(\d{1,2}|zero|one|two|three|four|five|six|seven|eight|nine|ten)\b""")
            .find(normalized)?.groupValues?.getOrNull(1)
        if (marker != null) return marker.toIntOrNull() ?: englishSmallNumber(marker)

        val trailing = Regex("""(?:^|\s)(0|[2-9]|1[0-9]|2[0-5])$""").find(normalized)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (trailing != null) return trailing

        val roman = romanSeasonPattern.find(normalized)?.value?.trim()?.lowercase(Locale.ROOT)
        return romanValues.firstOrNull { (token, _) -> roman == token }?.second
    }

    private fun englishSmallNumber(value: String): Int? = when (value) {
        "zero" -> 0
        "one" -> 1
        "two" -> 2
        "three" -> 3
        "four" -> 4
        "five" -> 5
        "six" -> 6
        "seven" -> 7
        "eight" -> 8
        "nine" -> 9
        "ten" -> 10
        else -> null
    }

    fun isNonVideoType(type: TvType?): Boolean {
        if (type == null) return false
        return type in setOf(TvType.Music, TvType.Audio, TvType.AudioBook, TvType.Podcast, TvType.Live)
    }

    fun isTypeCompatible(type: TvType?, isMovie: Boolean?): Boolean {
        if (type == null || isMovie == null) return true
        if (isNonVideoType(type)) return false
        val episodicTypes = setOf(TvType.TvSeries, TvType.Anime, TvType.AsianDrama)
        val movieOnlyTypes = setOf(TvType.Movie, TvType.AnimeMovie)
        return if (isMovie) type !in episodicTypes else type !in movieOnlyTypes
    }

    private fun getResultYear(result: SearchResponse): Int? {
        return try {
            val field = result.javaClass.getDeclaredField("year")
            field.isAccessible = true
            (field.get(result) as? Number)?.toInt()
        } catch (_: Exception) {
            try {
                val method = result.javaClass.getMethod("getYear")
                (method.invoke(result) as? Number)?.toInt()
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun parseYearFromTitle(title: String): Int? {
        val normalized = Normalizer.normalize(title, Normalizer.Form.NFKC)
        val parenthesizedYear = parenthesizedYearPattern.findAll(normalized)
            .lastOrNull()?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (parenthesizedYear != null) return parenthesizedYear
        return trailingYearPattern.find(normalized)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }
}
