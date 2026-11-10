package com.kitsugi.animelist.data.cloudstream

import android.util.Log
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import java.text.Normalizer
import java.util.Locale

/**
 * Bölüm verisi çıkarma yardımcıları.
 * [CsStreamRunner] içindeki episode matching mantığı buraya taşındı.
 */
internal object CsEpisodeMatcher {

    private const val TAG = "CsEpisodeMatcher"

    /** Exact, boundary-aware recognition of common English/Turkish/Japanese/Chinese episode labels. */
    fun episodeNameMatchesTarget(name: String, season: Int, episode: Int): Boolean {
        if (season < 1 || episode < 1) return false
        val explicitSeason = CsTitleMatcher.parseExplicitSeasonFromTitle(name)
        if (explicitSeason != null && explicitSeason != season) return false

        val normalized = Normalizer.normalize(name, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .replace('ı', 'i')
        val s = season.toString()
        val e = episode.toString()
        val seasonEpisodePatterns = listOf(
            Regex("""(?<![\p{L}\p{N}])s0*${s}[\s._-]*e0*${e}(?![0-9])"""),
            Regex("""(?<![0-9])0*${s}\s*x\s*0*${e}(?![0-9])"""),
            Regex("""(?<![\p{L}\p{N}])(?:season|sezon)\s*0*${s}[^0-9]{0,16}(?:episode|ep|bölüm|bolum)\s*0*${e}(?![0-9])"""),
            Regex("""(?<![0-9])0*${s}\s*[.º°]?\s*(?:sezon|season)\b.{0,24}?(?<![0-9])0*${e}\s*[.º°]?\s*(?:episode|ep|bölüm|bolum)\b"""),
            Regex("""第0*${s}\s*(?:期|季)[\s,·-]*第0*${e}\s*(?:話|话|集)""")
        )
        if (seasonEpisodePatterns.any { it.containsMatchIn(normalized) }) return true
        if (season != 1) return false

        val seasonOneEpisodePatterns = listOf(
            Regex("""(?<![\p{L}\p{N}])(?:episode|ep|bölüm|bolum)\s*\.?\s*0*${e}(?![0-9])"""),
            Regex("""(?<![0-9])0*${e}\s*[.º°]?\s*(?:episode|ep|bölüm|bolum)\b"""),
            Regex("""第0*${e}\s*(?:話|话|集)""")
        )
        return seasonOneEpisodePatterns.any { it.containsMatchIn(normalized) }
    }

    /**
     * Sayfanın (LoadResponse) kendi beyan ettiği sezon numarası; yoksa null.
     *
     * Kaynak: başlık ("Supernatural 2. Sezon") veya URL slug'ı ("/supernatural-2-sezon").
     * Çağıran taraf, istenen sezonla çelişip çelişmediğini bu değerle anlar.
     */
    fun pageSeasonOf(response: LoadResponse): Int? {
        return try {
            val name = when (response) {
                is AnimeLoadResponse -> response.name
                is TvSeriesLoadResponse -> response.name
                else -> null
            }
            // 1) Başlık: güvenilir kaynak ("Supernatural 2. Sezon", "Season 2", "II. Sezon")
            CsTitleMatcher.parseSeasonFromTitle(name.orEmpty())?.let { return it }
            // 2) Slug: YALNIZCA açık sezon işareti varsa ("/supernatural-2-sezon").
            //    Çıplak sondaki sayı (/dizi/1/2 → bölüm numarası olabilir) sezon sayılmaz;
            //    aksi hâlde yanlış "sezon çelişkisi" tespiti doğru bölümün oynatılmasını engellerdi.
            val url = response.url
            val path = try {
                java.net.URI(url).rawPath.orEmpty()
            } catch (_: Exception) {
                url.substringBefore('?')
            }
            // Türkçe sitelerin iki yaygın kalıbı vardır:
            //   /supernatural-2-sezon          → sezon anahtar sözcükten ÖNCE
            //   /sezon-2/bolum-3, season-2/... → sezon anahtar sözcükten SONRA
            // "sezon"dan sonraki ilk sayı tek başına sezon sayılırsa
            // "/...-2-sezon-3-bolum" adresi yanlışlıkla "sezon 3" okunur ve doğru bölüm
            // yanlış sezon çelişkisiyle engellenir. Bu yüzden ÖNCE anahtar sözcükten önceki
            // sayı, yoksa sonraki sayı dikkate alınır.
            val seasonBeforeKeyword = Regex(
                """(?<!\d)(\d{1,2})[\s._-]*(?:sezon|season)(?![\p{L}])""",
                RegexOption.IGNORE_CASE
            ).find(path)?.groupValues?.getOrNull(1)?.toIntOrNull()
            val seasonAfterKeyword = Regex(
                """(?:sezon|season)[\s._-]*(\d{1,2})(?!\d)""",
                RegexOption.IGNORE_CASE
            ).find(path)?.groupValues?.getOrNull(1)?.toIntOrNull()
            (seasonBeforeKeyword ?: seasonAfterKeyword)?.takeIf { it in 1..25 }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Seasons explicitly declared by the response's episodes, either in the season field or
     * in an episode label such as "S2E5" / "2. Sezon 5. Bölüm".
     */
    fun episodeSeasonsOf(response: LoadResponse): Set<Int> {
        return try {
            allEpisodeObjects(response).mapNotNull { getDeclaredEpisodeSeason(it) }.toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    /**
     * Whether the page's explicit season contradicts the requested season and its episode list
     * does not reliably prove that the requested season is present.
     */
    fun hasSeasonConflict(response: LoadResponse, season: Int): Boolean {
        val pageSeason = pageSeasonOf(response) ?: return false
        if (pageSeason == season) return false

        val episodeSeasons = episodeSeasonsOf(response)
        if (season !in episodeSeasons) return true

        // Some providers put every episode of an isolated season page in season=1. When the
        // page itself explicitly says S2+ and every episode is only implicitly tagged S1, that
        // raw value must not make an S1 request look present on the S2 page. Explicit S1 labels,
        // or a genuinely multi-season list, remain valid evidence for S1.
        if (season == 1 && pageSeason > 1 && episodeSeasons == setOf(1)) {
            val episodes = allEpisodeObjects(response)
            val onlyImplicitSeasonOne = episodes.isNotEmpty() && episodes.all { ep ->
                getEpisodeSeason(ep) == 1 && getExplicitEpisodeSeason(ep) == null
            }
            if (onlyImplicitSeasonOne) return true
        }
        return false
    }

    /** The page URL may serve as episode data only when the list is empty and no season conflicts. */
    fun canUsePageUrlFallback(response: LoadResponse, season: Int): Boolean =
        allEpisodeObjects(response).isEmpty() && !hasSeasonConflict(response, season)

    private fun allEpisodeObjects(response: LoadResponse): List<Any> = when (response) {
        is AnimeLoadResponse -> response.episodes.values.flatten()
        is TvSeriesLoadResponse -> response.episodes
        else -> emptyList()
    }

    private fun getExplicitEpisodeSeason(ep: Any): Int? =
        getEpisodeName(ep)?.let { CsTitleMatcher.parseExplicitSeasonFromTitle(it) }

    private fun getDeclaredEpisodeSeason(ep: Any): Int? =
        getExplicitEpisodeSeason(ep) ?: getEpisodeSeason(ep)

    /**
     * A positional fallback is safe only when the selected season's episodes carry no episode
     * numbers. A contiguous sequence above 1 is not enough to prove whether numbers are absolute
     * or local, so numbered mismatches are deliberately not remapped by index.
     */
    private fun canUseRelativeIndexFallback(episodes: List<Any>): Boolean =
        episodes.isNotEmpty() && episodes.all { getEpisodeNumber(it) == null }

    /**
     * Verilen [LoadResponse] içinde belirtilen sezon ve bölüme karşılık gelen
     * bölüm verisini (episode data string) döndürür.
     * Bulunamazsa `null` döner.
     *
     * @param allowSeasonConflict Sayfa AÇIKÇA başka bir sezona aitse (ör. aranan S1, sayfa
     *   "2. Sezon") sezon-bağımsız geri dönüşler (bölüm numarasına göre arama) devre dışı
     *   bırakılır. Varsayılan `false`: yanlış sezonun bölümünü oynatmak yerine `null` döner
     *   ve çağıran taraf sezon sayfasına inmeyi dener.
     */
    fun findEpisodeData(
        response: LoadResponse,
        season: Int,
        episode: Int,
        allowSeasonConflict: Boolean = false
    ): String? {
        return try {
            when (response) {
                is AnimeLoadResponse -> findInAnimeResponse(response, season, episode, allowSeasonConflict)
                is TvSeriesLoadResponse -> findInTvSeriesResponse(response, season, episode, allowSeasonConflict)
                is MovieLoadResponse -> {
                    Log.d(TAG, "MovieLoadResponse: dataUrl=${response.dataUrl}")
                    if (response.dataUrl.isNullOrBlank()) response.url else response.dataUrl
                }
                else -> {
                    Log.w(TAG, "Bilinmeyen LoadResponse tipi: ${response.javaClass.name}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "findEpisodeData HATA", e)
            null
        }
    }

    /**
     * Returns every distinct episode-data value for the requested S/E. Anime providers can put
     * subbed and dubbed episodes in separate DubStatus buckets (Anizium v25 does); the legacy
     * singular matcher intentionally keeps its Subbed-first behavior for callers that need one.
     */
    fun findEpisodeDataCandidates(
        response: LoadResponse,
        season: Int,
        episode: Int,
        allowSeasonConflict: Boolean = false
    ): List<String> {
        return try {
            when (response) {
                is AnimeLoadResponse -> {
                    val statusOrder = listOf(
                        com.lagradost.cloudstream3.DubStatus.Subbed,
                        com.lagradost.cloudstream3.DubStatus.Dubbed,
                        com.lagradost.cloudstream3.DubStatus.None
                    ) + response.episodes.keys.filter { status ->
                        status !in setOf(
                            com.lagradost.cloudstream3.DubStatus.Subbed,
                            com.lagradost.cloudstream3.DubStatus.Dubbed,
                            com.lagradost.cloudstream3.DubStatus.None
                        )
                    }
                    statusOrder.asSequence()
                        .filter { status -> !response.episodes[status].isNullOrEmpty() }
                        .mapNotNull { status ->
                            findInAnimeResponse(
                                response = response,
                                season = season,
                                episode = episode,
                                allowSeasonConflict = allowSeasonConflict,
                                onlyDubStatus = status
                            )
                        }
                        .distinct()
                        .toList()
                }
                is TvSeriesLoadResponse -> listOfNotNull(
                    findInTvSeriesResponse(response, season, episode, allowSeasonConflict)
                )
                is MovieLoadResponse -> listOfNotNull(
                    if (response.dataUrl.isNullOrBlank()) response.url else response.dataUrl
                )
                else -> emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "findEpisodeDataCandidates HATA", e)
            emptyList()
        }
    }

    /**
     * Seçilen bölümün hangi [com.lagradost.cloudstream3.DubStatus] kovasından geldiğini döndürür.
     *
     * Dönüş: "dub" | "sub" | null (bilinmiyor).
     *
     * Bu, dil rozetinin **kaynağın kendi meta verisine** dayanmasını sağlar; dosya adından
     * tahmin yürütmek yerine sağlayıcının gerçekte ne söylediğini kullanırız.
     */
    fun findDubStatusForEpisodeData(response: LoadResponse, episodeData: String?): String? {
        if (episodeData.isNullOrBlank()) return null
        val anime = response as? AnimeLoadResponse ?: return null
        return try {
            var result: String? = null
            for ((status, episodes) in anime.episodes) {
                val matches = episodes.any { ep -> getEpisodeData(ep) == episodeData }
                if (matches) {
                    result = when (status) {
                        com.lagradost.cloudstream3.DubStatus.Dubbed -> "dub"
                        com.lagradost.cloudstream3.DubStatus.Subbed -> "sub"
                        else -> null
                    }
                    break
                }
            }
            result
        } catch (e: Exception) {
            Log.w(TAG, "findDubStatusForEpisodeData HATA: ${e.message}")
            null
        }
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private fun findInAnimeResponse(
        response: AnimeLoadResponse,
        season: Int,
        episode: Int,
        allowSeasonConflict: Boolean = false,
        onlyDubStatus: com.lagradost.cloudstream3.DubStatus? = null
    ): String? {
        val allEntries = response.episodes.entries
        Log.d(TAG, "AnimeLoadResponse: ${allEntries.size} sezon bucket(ı)")
        allEntries.forEach { (dubStatus, eps) ->
            Log.d(TAG, "  DubStatus=$dubStatus bölüm sayısı=${eps.size}")
        }

        val responseName = response.name ?: ""
        val responseUrl = response.url ?: ""
        val foundSeason = CsTitleMatcher.parseSeasonFromTitle(responseName)
            ?: CsTitleMatcher.parseSeasonFromSlug(responseUrl)

        val allEpisodesList = response.episodes.values.flatten()
        val declaredSeasonsAnime = allEpisodesList.mapNotNull { getDeclaredEpisodeSeason(it) }.toSet()
        val hasMultipleDeclaredSeasonsAnime = declaredSeasonsAnime.size > 1
        val treatSeason1AsTarget = !hasMultipleDeclaredSeasonsAnime && !declaredSeasonsAnime.contains(season) && foundSeason != null && foundSeason == season

        // Sayfa açıkça BAŞKA bir sezona aitse (ör. aranan S1, sayfa "2. Sezon"):
        // sezon-bağımsız geri dönüşler kapatılır. Aksi hâlde bu sayfanın 1. bölümü
        // S1E1 sanılıp oynatılıyordu (yanlış sezon + yanlış bölüm).
        val seasonConflict = !allowSeasonConflict && hasSeasonConflict(response, season)
        val seasonAgnosticAllowed = !seasonConflict
        if (seasonConflict) {
            Log.w(
                TAG,
                "Anime: sezon çelişkisi — sayfa=${foundSeason ?: "?"} istenen=S$season; bölüm eşlemesi durduruldu"
            )
            return null
        }

        val getEffectiveSeason = { ep: Any ->
            val explicitEpisodeSeason = getExplicitEpisodeSeason(ep)
            if (explicitEpisodeSeason != null) {
                explicitEpisodeSeason
            } else {
                val epSeason = getEpisodeSeason(ep)
                if (treatSeason1AsTarget && (epSeason == null || epSeason == 1)) {
                    season
                } else {
                    epSeason ?: 1
                }
            }
        }

        // Keep the legacy singular lookup Subbed-first. The candidate lookup can constrain this
        // pass to one status bucket so separate dub/sub episode data are both returned.
        val subEpisodes = response.episodes[com.lagradost.cloudstream3.DubStatus.Subbed] ?: emptyList()
        val dubEpisodes = response.episodes[com.lagradost.cloudstream3.DubStatus.Dubbed] ?: emptyList()
        val noneEpisodes = response.episodes[com.lagradost.cloudstream3.DubStatus.None] ?: emptyList()

        val preferredEpisodes = onlyDubStatus?.let { response.episodes[it].orEmpty() } ?: when {
            subEpisodes.isNotEmpty() -> subEpisodes
            dubEpisodes.isNotEmpty() -> dubEpisodes
            else -> noneEpisodes
        }

        // Restrict candidate mode to one DubStatus bucket; otherwise retain legacy cross-bucket fallback.
        val allEpisodes = if (onlyDubStatus != null) {
            response.episodes[onlyDubStatus].orEmpty()
        } else {
            allEntries.flatMap { it.value }
        }

        // Try: season+episode exact match on preferred bucket
        var match = preferredEpisodes.find { ep ->
            val epNum = getEpisodeNumber(ep) ?: return@find false
            val epSeason = getEffectiveSeason(ep)
            epSeason == season && epNum == episode
        }

        // Fallback 1: search across ALL episode buckets
        if (match == null) {
            match = allEpisodes.find { ep ->
                val epNum = getEpisodeNumber(ep) ?: return@find false
                val epSeason = getEffectiveSeason(ep)
                epSeason == season && epNum == episode
            }
        }

        // Fallback 1b: search by name matching the target season and episode (e.g. "S2E5", "2. Sezon 5. Bölüm")
        if (match == null) {
            match = allEpisodes.find { ep ->
                val epName = getEpisodeName(ep) ?: return@find false
                episodeNameMatchesTarget(epName, season, episode)
            }
            if (match != null) Log.d(TAG, "Anime: Name-based season match: '${getEpisodeName(match)}' for S${season}E${episode}")
        }

        // Fallback 2: match by episode number only (season-agnostic) within preferred bucket
        if (match == null && seasonAgnosticAllowed && (season == 1 || treatSeason1AsTarget)) {
            match = preferredEpisodes.find { ep ->
                getEffectiveSeason(ep) == season && (getEpisodeNumber(ep) ?: -1) == episode
            }
            if (match != null) Log.d(TAG, "Preferred bucket sezon-bağımsız eşleşme: ep=$episode")
        }

        // Fallback 3: match by episode number across all buckets
        if (match == null && seasonAgnosticAllowed && (season == 1 || treatSeason1AsTarget)) {
            match = allEpisodes.find { ep ->
                getEffectiveSeason(ep) == season && (getEpisodeNumber(ep) ?: -1) == episode
            }
            if (match != null) Log.d(TAG, "Sezon bağımsız bölüm eşleşmesi kullanıldı: ep=$episode")
        }

        // Fallback 4: none of the episodes have season/episode fields set at all
        if (
            match == null && seasonAgnosticAllowed && (season == 1 || treatSeason1AsTarget) &&
            preferredEpisodes.isNotEmpty()
        ) {
            val allHaveNoMeta = preferredEpisodes.all { ep ->
                getEpisodeNumber(ep) == null && getDeclaredEpisodeSeason(ep) == null
            }
            if (allHaveNoMeta) {
                val idx = episode - 1  // episode is 1-based
                if (idx in preferredEpisodes.indices) {
                    match = preferredEpisodes[idx]
                    Log.d(TAG, "İndeks-bazlı fallback kullanıldı (meta yok): bucket[${idx}] → ep=$episode")
                }
            }
        }

        // Fallback 5: a lone episode is usable only when its season is supported by the page
        // and its episode number is absent or agrees with the requested episode.
        val onlyEpisode = allEpisodes.singleOrNull()
        if (
            match == null && seasonAgnosticAllowed && episode == 1 && onlyEpisode != null &&
            getEffectiveSeason(onlyEpisode) == season &&
            (getEpisodeNumber(onlyEpisode) == null || getEpisodeNumber(onlyEpisode) == episode)
        ) {
            match = onlyEpisode
            Log.d(TAG, "Tek bölüm fallback kullanıldı (sezon/bölüm kanıtı uyumlu)")
        }

        // Fallback 6: position is used only for season-matched episodes with no episode number.
        if (match == null && preferredEpisodes.isNotEmpty()) {
            val sameSeasonEps = preferredEpisodes.filter { ep -> getEffectiveSeason(ep) == season }
            if (canUseRelativeIndexFallback(sameSeasonEps)) {
                val sortedEps = sameSeasonEps.sortedBy { getEpisodeNumber(it) ?: 0 }
                val idx = episode - 1
                if (idx in sortedEps.indices) {
                    match = sortedEps[idx]
                    Log.d(TAG, "Anime (preferred): numberless season-index fallback S${season}[${idx}] → requested E$episode")
                }
            }
        }

        // Fallback 7: the same numberless positional selection across available episodes.
        // In the singular lookup this spans DubStatus buckets; candidate lookup stays in one bucket.
        if (match == null && allEpisodes.isNotEmpty()) {
            val sameSeasonEps = allEpisodes.filter { ep -> getEffectiveSeason(ep) == season }
            if (canUseRelativeIndexFallback(sameSeasonEps)) {
                val sortedEps = sameSeasonEps.sortedBy { getEpisodeNumber(it) ?: 0 }
                val idx = episode - 1
                if (idx in sortedEps.indices) {
                    match = sortedEps[idx]
                    Log.d(TAG, "Anime (all): numberless season-index fallback S${season}[${idx}] → requested E$episode")
                }
            }
        }

        return match?.let { getEpisodeData(it) }
    }

    private fun findInTvSeriesResponse(
        response: TvSeriesLoadResponse,
        season: Int,
        episode: Int,
        allowSeasonConflict: Boolean = false
    ): String? {
        Log.d(TAG, "TvSeriesLoadResponse: ${response.episodes.size} bölüm")

        val responseName = response.name ?: ""
        val responseUrl = response.url ?: ""
        val foundSeason = CsTitleMatcher.parseSeasonFromTitle(responseName)
            ?: CsTitleMatcher.parseSeasonFromSlug(responseUrl)

        val declaredSeasons = response.episodes.mapNotNull { getDeclaredEpisodeSeason(it) }.toSet()
        val hasMultipleDeclaredSeasons = declaredSeasons.size > 1
        val treatSeason1AsTarget = !hasMultipleDeclaredSeasons && !declaredSeasons.contains(season) && foundSeason != null && foundSeason == season

        val seasonConflict = !allowSeasonConflict && hasSeasonConflict(response, season)
        val seasonAgnosticAllowed = !seasonConflict
        if (seasonConflict) {
            Log.w(
                TAG,
                "TvSeries: sezon çelişkisi — sayfa=${foundSeason ?: "?"} istenen=S$season; bölüm eşlemesi durduruldu"
            )
            return null
        }

        val getEffectiveSeason = { ep: Any ->
            val explicitEpisodeSeason = getExplicitEpisodeSeason(ep)
            if (explicitEpisodeSeason != null) {
                explicitEpisodeSeason
            } else {
                val epSeason = getEpisodeSeason(ep)
                if (treatSeason1AsTarget && (epSeason == null || epSeason == 1)) {
                    season
                } else {
                    epSeason ?: 1
                }
            }
        }

        var match = response.episodes.find { ep ->
            val epNum = getEpisodeNumber(ep) ?: return@find false
            val epSeason = getEffectiveSeason(ep)
            epSeason == season && epNum == episode
        }

        // Fallback 1: name-based match (e.g. "S2E5", "2. Sezon 5. Bölüm") — before index fallbacks
        if (match == null) {
            match = response.episodes.find { ep ->
                val epName = getEpisodeName(ep) ?: return@find false
                episodeNameMatchesTarget(epName, season, episode)
            }
            if (match != null) Log.d(TAG, "TvSeries: Name-based season match: '${getEpisodeName(match)}' for S${season}E${episode}")
        }

        // Fallback 2: episode number only (season-agnostic)
        if (match == null && seasonAgnosticAllowed && (season == 1 || treatSeason1AsTarget)) {
            match = response.episodes.find { ep ->
                getEffectiveSeason(ep) == season && (getEpisodeNumber(ep) ?: -1) == episode
            }
            if (match != null) Log.d(TAG, "TvSeries: sezon bağımsız fallback ep=$episode")
        }

        // Fallback 3: accept a lone episode only if it belongs to the requested season and
        // does not declare a different episode number.
        val onlyEpisode = response.episodes.singleOrNull()
        if (
            match == null && seasonAgnosticAllowed && episode == 1 && onlyEpisode != null &&
            getEffectiveSeason(onlyEpisode) == season &&
            (getEpisodeNumber(onlyEpisode) == null || getEpisodeNumber(onlyEpisode) == episode)
        ) {
            match = onlyEpisode
            Log.d(TAG, "TvSeries: tek bölüm fallback kullanıldı (sezon/bölüm kanıtı uyumlu)")
        }

        // Fallback 4: index-based — used when ALL episodes have no metadata at all (flat list, no season/ep fields)
        if (
            match == null && seasonAgnosticAllowed && (season == 1 || treatSeason1AsTarget) &&
            response.episodes.isNotEmpty()
        ) {
            val allHaveNoMeta = response.episodes.all { ep ->
                getEpisodeNumber(ep) == null && getDeclaredEpisodeSeason(ep) == null
            }
            if (allHaveNoMeta) {
                val idx = episode - 1  // episode is 1-based
                if (idx in response.episodes.indices) {
                    match = response.episodes[idx]
                    Log.d(TAG, "TvSeries: indeks-bazlı fallback kullanıldı (meta yok): [$idx] → ep=$episode")
                }
            }
        }

        // Fallback 5: season matched but episode field is null/missing — use positional index within that season
        // (DiziBox style: episodes have season set, but episode number is always null).
        if (match == null && response.episodes.isNotEmpty()) {
            val sameSeasonEps = response.episodes.filter { ep -> getEffectiveSeason(ep) == season }
            if (sameSeasonEps.isNotEmpty()) {
                val allEpNull = sameSeasonEps.all { getEpisodeNumber(it) == null }
                if (allEpNull) {
                    val idx = episode - 1
                    if (idx in sameSeasonEps.indices) {
                        match = sameSeasonEps[idx]
                        Log.d(TAG, "TvSeries: season-based index fallback (episode null): S${season}[${idx}] → ep=$episode")
                    }
                }
            }
        }

        // Fallback 6: use relative position only when the matched season has no episode numbers.
        if (match == null && response.episodes.isNotEmpty()) {
            val sameSeasonEps = response.episodes.filter { ep -> getEffectiveSeason(ep) == season }
            if (canUseRelativeIndexFallback(sameSeasonEps)) {
                val sortedEps = sameSeasonEps.sortedBy { getEpisodeNumber(it) ?: 0 }
                val idx = episode - 1
                if (idx in sortedEps.indices) {
                    match = sortedEps[idx]
                    Log.d(TAG, "TvSeries: numberless season-index fallback S${season}[${idx}] → requested E$episode")
                }
            }
        }

        return match?.let { getEpisodeData(it) }
    }

    // ─── Reflection helpers ──────────────────────────────────────────────────

    fun getEpisodeNumber(ep: Any): Int? {
        return try { getField(ep, "episode") }
        catch (_: Exception) { null }
    }

    fun getEpisodeSeason(ep: Any): Int? {
        return try { getField(ep, "season") }
        catch (_: Exception) { null }
    }

    fun getEpisodeName(ep: Any): String? {
        return try {
            val field = ep.javaClass.getDeclaredField("name")
            field.isAccessible = true
            field.get(ep) as? String
        } catch (_: Exception) {
            try {
                ep.javaClass.getMethod("getName").invoke(ep) as? String
            } catch (_: Exception) { null }
        }
    }

    fun getEpisodeData(ep: Any): String? {
        return try {
            val field = ep.javaClass.getDeclaredField("data")
            field.isAccessible = true
            field.get(ep) as? String
        } catch (_: Exception) {
            try {
                ep.javaClass.getMethod("getData").invoke(ep) as? String
            } catch (_: Exception) { null }
        }
    }

    private fun getField(obj: Any, name: String): Int? {
        // Walk the class hierarchy to find the field
        var clazz: Class<*>? = obj.javaClass
        while (clazz != null) {
            try {
                val field = clazz.getDeclaredField(name)
                field.isAccessible = true
                val value = field.get(obj)
                return when (value) {
                    is Number -> value.toInt()
                    is String -> value.toIntOrNull()
                    else -> value?.toString()?.toIntOrNull()
                }
            } catch (_: NoSuchFieldException) {
                clazz = clazz.superclass
            } catch (_: Exception) {
                break
            }
        }
        // Fallback: try getter method
        return try {
            val getter = obj.javaClass.getMethod("get${name.replaceFirstChar { it.uppercase() }}")
            val value = getter.invoke(obj)
            when (value) {
                is Number -> value.toInt()
                is String -> value.toIntOrNull()
                else -> value?.toString()?.toIntOrNull()
            }
        } catch (_: Exception) { null }
    }
}
