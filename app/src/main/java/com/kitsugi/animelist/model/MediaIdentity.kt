package com.kitsugi.animelist.model

import java.text.Normalizer
import java.util.Locale

/** Provider IDs are namespaced by media type. A title must never override conflicting IDs. */
object MediaIdentity {
    private val nonIdentityCharacters = Regex("[^\\p{L}\\p{N}]")
    private val seasonOrdinalPatterns: List<Pair<Regex, String>> = listOf(
        Regex("\\b(?:2nd|second)\\s+season\\b", RegexOption.IGNORE_CASE) to "season 2",
        Regex("\\b(?:3rd|third)\\s+season\\b", RegexOption.IGNORE_CASE) to "season 3",
        Regex("\\b(?:4th|fourth)\\s+season\\b", RegexOption.IGNORE_CASE) to "season 4",
        Regex("\\b(?:5th|fifth)\\s+season\\b", RegexOption.IGNORE_CASE) to "season 5",
        Regex("\\b(?:6th|sixth)\\s+season\\b", RegexOption.IGNORE_CASE) to "season 6",
        Regex("\\b(?:7th|seventh)\\s+season\\b", RegexOption.IGNORE_CASE) to "season 7",
        Regex("\\bpart\\s+ii\\b", RegexOption.IGNORE_CASE) to "part 2",
        Regex("\\bpart\\s+iii\\b", RegexOption.IGNORE_CASE) to "part 3",
        Regex("\\bpart\\s+iv\\b", RegexOption.IGNORE_CASE) to "part 4"
    )

    fun canonicalSource(source: String): String = when (val value = source.trim().lowercase(Locale.ROOT)) {
        "jikan", "myanimelist" -> "mal"
        "al" -> "anilist"
        "shiki" -> "shikimori"
        "bgm", "bgm.tv" -> "bangumi"
        "themoviedb", "tmdb_account" -> "tmdb"
        else -> value
    }

    /**
     * `malId` is a legacy field name: for several providers it stores that provider's own ID.
     * The source is part of the ID namespace. Shikimori is the exception that must share the
     * MAL axis: its anime/manga `target_id` is the MAL ID (the official API exposes the same
     * value as `myanimelist_id`). Keeping it in an isolated Shikimori-only namespace splits
     * localized copies of the same production into different cross-sync groups.
     */
    // PERF: keys() her tablo emisyonunda satır başına çağrılır; aşağıdaki sabit koleksiyonlar
    // eskiden çağrı başına yeniden tahsis ediliyordu (2000 satır × emisyon = binlerce
    // gereksiz küme/liste tahsisi → GC baskısı).
    private val identityCapableTypes: List<MediaType> = listOf(MediaType.Anime, MediaType.Manga)

    fun keys(entry: MediaEntry): Set<String> = buildSet {
        val prefix = entry.type.name
        val source = canonicalSource(entry.source)
        entry.malId?.let { id ->
            when {
                // Legacy TMDB catalog rows stored the TMDB number in malId. Never expose
                // that value as MAL; retain it on the TMDB axis until migration rewrites it.
                source == "tmdb" && id > 0 -> add("$prefix:tmdb:$id")
                source == "shikimori" && id in 1..99_999_999 && entry.type in identityCapableTypes -> {
                    // Shikimori anime/manga IDs and MAL IDs are the same catalog identity.
                    // Retain the provider key as an alias for legacy crosswalk data, but also
                    // expose the shared MAL key so Romaji and English library rows can meet.
                    add("$prefix:mal:$id")
                    add("$prefix:shikimori:$id")
                }
                source == "bangumi" && id in 1..99_999_999 -> add("$prefix:bangumi:$id")
                id in 1..99_999_999 && isRealMalIdSource(source) && entry.type in identityCapableTypes ->
                    add("$prefix:mal:$id")
                id in 100_000_001..299_999_999 && source == "anilist" -> add("$prefix:anilist:${id - 100_000_000}")
                id in 300_000_001..399_999_999 && source == "kitsu" -> add("$prefix:kitsu:${id - 300_000_000}")
                id in 400_000_001..499_999_999 && source == "shikimori" && entry.type in identityCapableTypes -> {
                    val targetId = id - 400_000_000
                    add("$prefix:mal:$targetId")
                    add("$prefix:shikimori:$targetId")
                }
                id in 500_000_001..599_999_999 && source == "bangumi" -> add("$prefix:bangumi:${id - 500_000_000}")
                // Legacy Kitsu rows sometimes stored the raw Kitsu ID without its 300M offset.
                // Keep those in the Kitsu namespace; never reinterpret them as MAL IDs.
                source == "kitsu" && id in 1..99_999_999 -> add("$prefix:kitsu:$id")
            }
        }
        entry.simklId?.takeIf { it > 0 }?.let { add("$prefix:simkl:$it") }
        entry.tmdbId?.takeIf { it > 0 }?.let { add("$prefix:tmdb:$it") }
    }

    /**
     * Canonical keys of [entry] plus [extras] — the cross-provider crosswalk keys learned for
     * this row elsewhere (see `MyListIdentityCrosswalk`).
     *
     * Bangumi/Kitsu rows have no native key in common with AniList or MAL, so learned extra
     * keys close that gap without touching the stored row. Shikimori already shares the MAL
     * axis; its provider key remains only as a source-specific alias.
     */
    fun keys(entry: MediaEntry, extras: Set<String>): Set<String> =
        if (extras.isEmpty()) keys(entry) else keys(entry) + extras

    /** Returns the value only when this record's source contract says it is a real MAL ID. */
    fun realMalId(entry: MediaEntry): Int? {
        val source = canonicalSource(entry.source)
        if (source == "shikimori") {
            if (entry.type !in identityCapableTypes) return null
            return shikimoriId(entry)
        }
        if (!isRealMalIdSource(source)) return null
        return entry.malId?.takeIf { it in 1..99_999_999 }
    }

    /** Shikimori target ID, which is also the MAL ID (raw or 400M stable-ID form). */
    fun shikimoriId(entry: MediaEntry): Int? {
        if (canonicalSource(entry.source) != "shikimori") return null
        val id = entry.malId ?: return null
        return when {
            id in 1..99_999_999 -> id
            id in 400_000_001..499_999_999 -> id - 400_000_000
            else -> null
        }
    }

    /** Provider-native Bangumi subject ID (supports old raw IDs and the 500M stable-ID form). */
    fun bangumiId(entry: MediaEntry): Int? {
        if (canonicalSource(entry.source) != "bangumi") return null
        val id = entry.malId ?: return null
        return when {
            id in 1..99_999_999 -> id
            id in 500_000_001..599_999_999 -> id - 500_000_000
            else -> null
        }
    }

    /** Provider-native Kitsu ID (supports old raw IDs and the 300M stable-ID form). */
    fun kitsuId(entry: MediaEntry): Int? {
        if (canonicalSource(entry.source) != "kitsu") return null
        val id = entry.malId ?: return null
        return when {
            id in 1..99_999_999 -> id
            id in 300_000_001..399_999_999 -> id - 300_000_000
            else -> null
        }
    }

    /** Provider-native AniList media ID when `malId` contains its synthetic stable ID. */
    fun aniListId(entry: MediaEntry): Int? {
        if (canonicalSource(entry.source) != "anilist") return null
        return entry.malId?.takeIf { it in 100_000_001..299_999_999 }?.minus(100_000_000)
    }

    private val realMalIdSources: Set<String> = setOf("mal", "simkl", "anilist", "manual")

    private fun isRealMalIdSource(source: String): Boolean =
        source in realMalIdSources

    fun normalizedTitle(title: String): String = Normalizer.normalize(title, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).replace(nonIdentityCharacters, "")

    /**
     * Normalized title plus canonical season/part ordinal synonyms (e.g. `"2nd Season"` ↔
     * `"Season 2"`), without loosening distinct sequel/season numbers.
     */
    fun normalizedTitleVariants(title: String): Set<String> {
        val base = normalizedTitle(title)
        if (base.isBlank()) return emptySet()
        val normalizedRaw = Normalizer.normalize(title, Normalizer.Form.NFKC)
        var rewritten = normalizedRaw
        seasonOrdinalPatterns.forEach { (pattern, replacement) ->
            rewritten = pattern.replace(rewritten, replacement)
        }
        val canonicalSeason = normalizedTitle(rewritten)
        return if (canonicalSeason.isNotBlank() && canonicalSeason != base) {
            setOf(base, canonicalSeason)
        } else {
            setOf(base)
        }
    }

    /** Provider-ID namespaces present on both entries but pointing at different IDs. */
    fun conflictingIdentityKeys(a: MediaEntry, b: MediaEntry): Set<String> =
        conflictingIdentityKeys(a.type, b.type, keys(a), keys(b))

    /**
     * [conflictingIdentityKeys] for precomputed key sets, so callers that merge crosswalk keys
     * into a row's identity can reuse the exact same rules.
     *
     * A namespace counts as conflicting only when the two sides share **no** value in it. That
     * matters because an enriched row can legitimately hold several IDs in one namespace (e.g. a
     * legacy Kitsu row plus a crosswalk hit); as soon as one value agrees the identity is proven.
     */
    fun conflictingIdentityKeys(
        typeA: MediaType?,
        typeB: MediaType?,
        keysA: Set<String>,
        keysB: Set<String>
    ): Set<String> {
        if (typeA != typeB) return emptySet()
        if (keysA.isEmpty() || keysB.isEmpty()) return emptySet()
        val byNamespaceA = keysA.groupByNamespace()
        val byNamespaceB = keysB.groupByNamespace()
        return byNamespaceA.keys.intersect(byNamespaceB.keys).filterTo(mutableSetOf()) { namespace ->
            byNamespaceA.getValue(namespace).intersect(byNamespaceB.getValue(namespace)).isEmpty()
        }
    }

    /**
     * Library grouping / deduplication conflict check.
     *
     * On TMDB, anime seasons do not have independent IDs — Simkl and ARM frequently attach
     * different `tmdbId` values (TV series vs compilation/season entry) to rows that already
     * agree on authoritative anime IDs (`mal`/`anilist`/`kitsu`/`shikimori`/`bangumi`/`simkl`)
     * or exact season title. A lone `Anime:tmdb` disagreement must not split a verified anime
     * group, while any primary anime-ID conflict still strictly vetoes merging.
     */
    fun authoritativeConflictingIdentityKeys(
        a: MediaEntry,
        b: MediaEntry,
        keysA: Set<String> = keys(a),
        keysB: Set<String> = keys(b)
    ): Set<String> {
        if (a.type != b.type) {
            if (!isAnimeMoviePair(a, b)) return setOf("Type:${a.type.name}!=${b.type.name}")
            val conflicts = mutableSetOf<String>()
            val malA = extractMalIdFromKeys(a, keysA)
            val malB = extractMalIdFromKeys(b, keysB)
            if (malA != null && malB != null && malA != malB) conflicts += "CrossType:mal"
            val simklA = a.simklId?.takeIf { it > 0 }
            val simklB = b.simklId?.takeIf { it > 0 }
            if (simklA != null && simklB != null && simklA != simklB) conflicts += "CrossType:simkl"
            val tmdbA = a.tmdbId?.takeIf { it > 0 }
            val tmdbB = b.tmdbId?.takeIf { it > 0 }
            val hasSharedPrimary = (malA != null && malA == malB) || (simklA != null && simklA == simklB)
            if (!hasSharedPrimary && tmdbA != null && tmdbB != null && tmdbA != tmdbB) {
                conflicts += "CrossType:tmdb"
            }
            return conflicts
        }
        val rawConflicts = conflictingIdentityKeys(a.type, b.type, keysA, keysB)
        if (rawConflicts.isEmpty()) return emptySet()
        if (a.type == MediaType.Anime && rawConflicts == setOf("Anime:tmdb")) {
            val primaryA = keysA.filterNotTo(HashSet()) { it.startsWith("Anime:tmdb:") }
            val primaryB = keysB.filterNotTo(HashSet()) { it.startsWith("Anime:tmdb:") }
            if (primaryA.intersect(primaryB).isNotEmpty() || libraryTitleMatch(a, b)) {
                return emptySet()
            }
        }
        return rawConflicts
    }

    /** `"Anime:mal:123"` → `("Anime:mal" to "123")`; namespaces may hold several values. */
    private fun Set<String>.groupByNamespace(): Map<String, Set<String>> =
        groupBy({ it.substringBeforeLast(':') }, { it.substringAfterLast(':') })
            .mapValues { (_, values) -> values.toSet() }

    fun sameMedia(
        a: MediaEntry,
        b: MediaEntry,
        allowTitle: Boolean = true,
        allowCrossType: Boolean = false
    ): Boolean = sameMedia(a, b, keys(a), keys(b), allowTitle, allowCrossType)

    /**
     * [sameMedia] against precomputed identity keys. [keysA]/[keysB] may carry crosswalk keys
     * (`MediaIdentity.keys(entry, extras)`) — everything else about the decision is identical.
     *
     * [allowCrossType] is intentionally opt-in for the combined library view. Import/update
     * code keeps provider categories separate; only that view may join an Anime row with a
     * Movie/TvShow row, and only with verified shared provider IDs (never title alone).
     */
    fun sameMedia(
        a: MediaEntry,
        b: MediaEntry,
        keysA: Set<String>,
        keysB: Set<String>,
        allowTitle: Boolean = true,
        allowCrossType: Boolean = false
    ): Boolean {
        if (a.type != b.type) {
            if (!allowCrossType) return false
            return sameVerifiedWatchableMedia(a, b, keysA, keysB)
        }
        if (conflictingIdentityKeys(a.type, b.type, keysA, keysB).isNotEmpty()) return false
        if (keysA.intersect(keysB).isNotEmpty()) return true
        if (!allowTitle) return false
        return titleMatch(a, b)
    }

    /**
     * Library-level identity check used for Listem ("Tümü" and per-provider deduplication):
     *  • Honors all primary provider ID conflicts,
     *  • Prevents two distinct anime seasons sharing one show-level `Anime:tmdb:<id>` from
     *    collapsing unless primary IDs or season titles also match,
     *  • Tolerates ±1 year release-date discrepancies across catalogs (winter cour Dec/Jan,
     *    JP vs international theatrical anime movie release) when titles match,
     *  • Bridges anime movies stored as [MediaType.Movie] (e.g. added via TMDB/Simkl movies)
     *    with [MediaType.Anime] records (e.g. Simkl `/anime`, AniList, MAL, Kitsu, Shikimori, Bangumi).
     */
    fun librarySameMedia(
        a: MediaEntry,
        b: MediaEntry,
        keysA: Set<String> = keys(a),
        keysB: Set<String> = keys(b),
        allowTitle: Boolean = true
    ): Boolean {
        if (a.type != b.type) {
            return sameAnimeMovieAcrossType(a, b, keysA, keysB, allowTitle)
        }
        if (authoritativeConflictingIdentityKeys(a, b, keysA, keysB).isNotEmpty()) return false
        val shared = keysA.intersect(keysB)
        if (shared.isNotEmpty()) {
            if (a.type == MediaType.Anime && shared.all { it.startsWith("Anime:tmdb:") }) {
                return allowTitle && libraryTitleMatch(a, b)
            }
            return true
        }
        if (!allowTitle) return false
        return libraryTitleMatch(a, b)
    }

    /**
     * True when an anime film exists once as [MediaType.Movie] (TMDB / Simkl `/movies`) and
     * once as [MediaType.Anime] (Simkl `/anime`, AniList, MAL, Kitsu, Shikimori, Bangumi).
     */
    fun sameAnimeMovieAcrossType(
        a: MediaEntry,
        b: MediaEntry,
        keysA: Set<String> = keys(a),
        keysB: Set<String> = keys(b),
        allowTitle: Boolean = true
    ): Boolean {
        if (!isAnimeMoviePair(a, b)) return false
        if (authoritativeConflictingIdentityKeys(a, b, keysA, keysB).isNotEmpty()) return false
        val anime = if (a.type == MediaType.Anime) a else b
        if ((anime.total != null && anime.total > 1) || anime.progress > 1) return false

        val simklA = a.simklId?.takeIf { it > 0 }
        val simklB = b.simklId?.takeIf { it > 0 }
        if (simklA != null && simklA == simklB) return true

        val malA = extractMalIdFromKeys(a, keysA)
        val malB = extractMalIdFromKeys(b, keysB)
        if (malA != null && malA == malB) return true

        val tmdbA = a.tmdbId?.takeIf { it > 0 }
        val tmdbB = b.tmdbId?.takeIf { it > 0 }
        if (tmdbA != null && tmdbB != null && tmdbA == tmdbB) {
            val yearMatches = a.year == null || b.year == null || a.year == b.year
            if (yearMatches && (!allowTitle || titleMatch(a, b) || a.title.isBlank() || b.title.isBlank())) {
                return true
            }
        }

        if (canonicalSource(a.source) == canonicalSource(b.source)) {
            return allowTitle && libraryTitleMatch(a, b)
        }

        return false
    }

    private fun isAnimeMoviePair(a: MediaEntry, b: MediaEntry): Boolean =
        (a.type == MediaType.Anime && b.type == MediaType.Movie) ||
            (a.type == MediaType.Movie && b.type == MediaType.Anime)

    private fun extractMalIdFromKeys(entry: MediaEntry, entryKeys: Set<String>): Int? {
        realMalId(entry)?.let { return it }
        return entryKeys.firstNotNullOfOrNull { key ->
            val parts = key.split(':')
            if (parts.size == 3 && parts[1] == "mal") parts[2].toIntOrNull()?.takeIf { it in 1..99_999_999 }
            else null
        }
    }

    private val strongAnimeProviders = setOf("mal", "anilist", "kitsu", "shikimori", "bangumi")

    private fun sameVerifiedWatchableMedia(
        a: MediaEntry,
        b: MediaEntry,
        keysA: Set<String>,
        keysB: Set<String>
    ): Boolean {
        val compatibleTypes = (a.type == MediaType.Anime && b.type in listOf(MediaType.Movie, MediaType.TvShow)) ||
            (b.type == MediaType.Anime && a.type in listOf(MediaType.Movie, MediaType.TvShow))
        if (!compatibleTypes) return false

        val providersA = keysA.groupByProviderNamespace()
        val providersB = keysB.groupByProviderNamespace()
        val sharedNamespaces = providersA.keys.intersect(providersB.keys)
        // A conflict in any common namespace blocks the merge even if another ID agrees.
        // This preserves the same conservative rule used for same-type identities.
        if (sharedNamespaces.any { namespace ->
                providersA.getValue(namespace).intersect(providersB.getValue(namespace)).isEmpty()
            }
        ) return false

        val agreeingNamespaces = sharedNamespaces.filterTo(mutableSetOf()) { namespace ->
            providersA.getValue(namespace).intersect(providersB.getValue(namespace)).isNotEmpty()
        }
        if (agreeingNamespaces.any { it in strongAnimeProviders }) return true

        // TMDB/Simkl movie and TV namespaces can overlap. A shared video ID is accepted only
        // together with an exact known release year and a matching title variant.
        return agreeingNamespaces.any { it == "tmdb" || it == "simkl" } &&
            a.year != null && b.year != null && a.year == b.year && titleMatch(a, b)
    }

    /** Removes only the media-type prefix; provider namespaces remain isolated. */
    private fun Set<String>.groupByProviderNamespace(): Map<String, Set<String>> =
        mapNotNull { key ->
            val parts = key.split(':')
            if (parts.size == 3) parts[1] to parts[2] else null
        }.groupBy({ it.first }, { it.second }).mapValues { (_, values) -> values.toSet() }

    /**
     * The two rows look like the same production but share no verified ID, so the catalog title
     * decides. A known release year on both sides must agree (a re-make with the identical title
     * is still a different production).
     */
    private fun titleMatch(a: MediaEntry, b: MediaEntry): Boolean =
        titleMatchWithYearTolerance(a, b, maxYearDelta = 0)

    fun libraryTitleMatch(a: MediaEntry, b: MediaEntry): Boolean =
        titleMatchWithYearTolerance(a, b, maxYearDelta = 1)

    private fun titleMatchWithYearTolerance(
        a: MediaEntry,
        b: MediaEntry,
        maxYearDelta: Int
    ): Boolean {
        if (a.year != null && b.year != null && kotlin.math.abs(a.year - b.year) > maxYearDelta) {
            return false
        }

        val normA = normalizedTitle(a.title)
        val normB = normalizedTitle(b.title)
        if (normA.isNotBlank() && normA == normB) return true

        val aTitles = listOfNotNull(a.title, a.titleEnglish)
            .flatMap(::normalizedTitleVariants)
            .filter { it.length >= 2 }
            .toSet()
        val bTitles = listOfNotNull(b.title, b.titleEnglish)
            .flatMap(::normalizedTitleVariants)
            .filter { it.length >= 2 }
            .toSet()
        if (aTitles.intersect(bTitles).isNotEmpty()) return true

        // Yalnızca Japonca/CJK başlık eşleştiğinde, iki kaydın ana başlıkları tamamen farklı Latin
        // adlar ise (örneğin Date A Bullet: Dead or Bullet vs Nightmare or Queen), farklı yapımlar say.
        // Ancak kaynaklardan biri CJK/Latin-dışı birincil ad taşıyorsa (ör. Bangumi `title = "怪獣8号"` /
        // `titleJapanese = null` ↔ AniList/MAL `title = "Kaijuu 8-gou"` / `titleJapanese = "怪獣８号"`),
        // hem `title ↔ titleJapanese` çapraz alan eşleşmesi hem `titleJapanese ↔ titleJapanese`
        // eşleşmesi uyumlu yıl + çelişmeyen bölüm sayısı ile aynı yapımı güvenle kanıtlar.
        val aAll = listOfNotNull(a.title, a.titleEnglish, a.titleJapanese)
            .map(::normalizedTitle).filter { it.length >= 2 }.toSet()
        val bAll = listOfNotNull(b.title, b.titleEnglish, b.titleJapanese)
            .map(::normalizedTitle).filter { it.length >= 2 }.toSet()
        if (aAll.intersect(bAll).isNotEmpty()) {
            if (normA.isBlank() || normB.isBlank() || normA.contains(normB) || normB.contains(normA)) {
                return true
            }
            val bothLatin = hasLatinLetters(a.title) && hasLatinLetters(b.title)
            val totalsConflict = a.total != null && b.total != null &&
                a.total > 0 && b.total > 0 && a.total != b.total
            if (!bothLatin && !totalsConflict) {
                return true
            }
        }
        return false
    }

    private fun hasLatinLetters(text: String?): Boolean =
        text?.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.LATIN } == true

    fun sameLibraryRecord(a: MediaEntry, b: MediaEntry): Boolean =
        canonicalSource(a.source) == canonicalSource(b.source) && sameMedia(a, b)
}
