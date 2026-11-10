package com.kitsugi.animelist.data.remote

import android.util.Log
import com.kitsugi.animelist.core.memory.BoundedCache
import com.kitsugi.animelist.model.MediaType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs
import kotlin.math.ln

/**
 * Her kaynak (Bangumi, AniList, MAL/Jikan, Kitsu, Shikimori, Simkl, TMDB ve eklenti vitrinleri)
 * için vitrin (Hero) öğelerinin:
 *  1) En yüksek kaliteli **dikey poster** (`bestPosterUrl`) ve
 *  2) En yüksek kaliteli **yatay arka plan / backdrop** (`bestBackdropUrl`)
 * görsellerini **TMDB** (`/original`) ve **Fanart.tv** (`tvposter`/`movieposter`,
 * `showbackground`/`moviebackground`) verilerini birleştirerek çözer.
 */
object KitsugiHeroArtworkResolver {
    private const val TAG = "HeroArtworkResolver"
    private const val TMDB_BASE_URL = "https://api.themoviedb.org/3"
    private const val TMDB_IMAGE_ORIGINAL = "https://image.tmdb.org/t/p/original"

    data class HeroArtwork(
        val bestPosterUrl: String? = null,
        val bestBackdropUrl: String? = null,
        val posterCandidates: List<String> = emptyList(),
        val backdropCandidates: List<String> = emptyList(),
        val resolvedTmdbId: Int? = null,
        val resolvedMalId: Int? = null,
        val resolvedTvdbId: Int? = null,
        val isMovie: Boolean = false
    ) {
        val hasAnyUpgrade: Boolean
            get() = !bestPosterUrl.isNullOrBlank() || !bestBackdropUrl.isNullOrBlank()
    }

    internal data class ArtworkCandidate(
        val url: String,
        val provider: String,
        val isBackdrop: Boolean,
        val width: Int = 0,
        val height: Int = 0,
        val language: String? = null,
        val voteAverage: Double = 0.0,
        val voteCount: Int = 0,
        val likes: Int = 0,
        val season: String? = null
    )

    private data class TmdbResolvedBundle(
        val tmdbId: Int,
        val isMovie: Boolean,
        val tvdbId: Int?,
        val posters: List<ArtworkCandidate>,
        val backdrops: List<ArtworkCandidate>
    )

    private data class TmdbSearchMatch(
        val tmdbId: Int,
        val isMovie: Boolean,
        val posterPath: String?,
        val backdropPath: String?,
        val score: Int
    )

    // Artwork entries retain multiple candidate URLs; cap this process-wide cache to limit heap growth.
    private val cache = BoundedCache<String, HeroArtwork>("heroArtwork", 300)
    private val inFlightMutex = Mutex()
    private val inFlight = HashMap<String, Deferred<HeroArtwork>>()
    private val resolverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun cacheKey(item: JikanSearchResult): String =
        "${item.source.lowercase()}:${item.type.name.lowercase()}:${item.malId}:${item.tmdbId ?: 0}:${item.title.lowercase()}"

    fun getCached(item: JikanSearchResult): HeroArtwork? = cache[cacheKey(item)]

    fun snapshotFor(items: List<JikanSearchResult>): Map<String, HeroArtwork> {
        if (items.isEmpty()) return emptyMap()
        val result = LinkedHashMap<String, HeroArtwork>(items.size)
        for (item in items) {
            val key = cacheKey(item)
            val hit = cache[key]
            if (hit != null) {
                result[key] = hit
            }
        }
        return result
    }

    /**
     * Mevcut öğeye önbellekteki veya anlık URL iyileştirmelerini uygular.
     * `malId`, `tmdbId`, `source`, `type` ve `cs3Url` alanlarına dokunmaz; böylece `exploreIdentity()`
     * ve liste eşleşmeleri (`getMediaEntryFromMap`) hiçbir zaman bozulmaz.
     */
    fun applyCachedOrInstant(
        item: JikanSearchResult,
        overrides: Map<String, HeroArtwork> = emptyMap()
    ): JikanSearchResult {
        val key = cacheKey(item)
        val artwork = overrides[key] ?: cache[key]
        val upgradedNativePoster = upgradeNativePosterUrl(item.imageUrl, item.source)
        val sanitizedNativeBackdrop = sanitizeNativeBackdropUrl(item.backdropUrl, upgradedNativePoster, item.source)

        val finalPoster = artwork?.bestPosterUrl?.takeIf { it.isNotBlank() } ?: upgradedNativePoster ?: item.imageUrl
        val finalBackdrop = if (item.type == MediaType.Manga) {
            sanitizedNativeBackdrop
        } else {
            artwork?.bestBackdropUrl?.takeIf { it.isNotBlank() } ?: sanitizedNativeBackdrop
        }

        if (finalPoster == item.imageUrl && finalBackdrop == item.backdropUrl) {
            return item
        }
        return item.copy(
            imageUrl = finalPoster,
            backdropUrl = finalBackdrop
        )
    }

    suspend fun enrichItem(item: JikanSearchResult): JikanSearchResult {
        val artwork = resolveArtwork(item)
        return applyCachedOrInstant(item, mapOf(cacheKey(item) to artwork))
    }

    /**
     * Kaynağın kendi döndürdüğü dikey poster URL'sini mümkün olan en yüksek çözünürlüğe yükseltir.
     */
    fun upgradeNativePosterUrl(url: String?, source: String = ""): String? {
        val trimmed = url?.trim()?.takeIf { it.isNotBlank() } ?: return null
        if (ShikimoriPosterResolver.isMissingImage(trimmed)) return null

        var upgraded = trimmed
        if (upgraded.startsWith("//")) {
            upgraded = "https:$upgraded"
        }

        // 1. TMDB: /w92, /w154, /w185, /w300, /w342, /w500, /w780, /w1280 → /original
        if (upgraded.contains("image.tmdb.org/t/p/")) {
            upgraded = upgraded.replace(
                Regex("https?://image\\.tmdb\\.org/t/p/(?:w\\d+|h\\d+)/"),
                "https://image.tmdb.org/t/p/original/"
            )
        }

        // 2. Bangumi: /r/400/pic/cover/l/... veya /pic/cover/c|m|s|g/... → /pic/cover/l/...
        if (upgraded.contains("bgm.tv/") || source.equals("bangumi", ignoreCase = true) || source.equals("bgm", ignoreCase = true)) {
            upgraded = upgraded
                .replace(Regex("https?://lain\\.bgm\\.tv/r/\\d+/pic/cover/[a-z]/"), "https://lain.bgm.tv/pic/cover/l/")
                .replace(Regex("https?://lain\\.bgm\\.tv/pic/cover/[cmsg]/"), "https://lain.bgm.tv/pic/cover/l/")
        }

        // 3. Kitsu: /poster_images/{id}/tiny|small|medium. → /poster_images/{id}/large.
        if (upgraded.contains("kitsu") && upgraded.contains("/poster_images/")) {
            upgraded = upgraded.replace(
                Regex("(/poster_images/\\d+/)(?:tiny|small|medium)(\\.[a-zA-Z0-9]+)"),
                "$1large$2"
            )
        }

        // 4. AniList: /cover/small|medium/ → /cover/large/
        if (upgraded.contains("anilist.co/") && upgraded.contains("/cover/")) {
            upgraded = upgraded.replace(
                Regex("/cover/(?:small|medium)/"),
                "/cover/large/"
            )
        }

        // 5. MyAnimeList CDN: /r/WxH/images/... → /images/...
        if (upgraded.contains("myanimelist.net/r/")) {
            upgraded = upgraded.replace(
                Regex("myanimelist\\.net/r/\\d+x\\d+/images/"),
                "myanimelist.net/images/"
            )
        }

        // 6. Shikimori: /preview/ veya /x96/ veya /x48/ → /original/
        if (upgraded.contains("shikimori") || source.equals("shikimori", ignoreCase = true)) {
            upgraded = upgraded.replace(
                Regex("/(?:preview|x96|x48)/"),
                "/original/"
            )
        }

        return upgraded
    }

    /**
     * Kaynağın kendi döndürdüğü yatay arka plan (backdrop) URL'sini doğrular ve yükseltir.
     * Bangumi kapakları gibi gerçekte dikey olan görselleri veya posterle bire bir aynı olan
     * URL'leri reddeder (`null` döndürür) ki dikey kapak yatay arka plan sanılıp kırpılmasın.
     */
    fun sanitizeNativeBackdropUrl(
        backdropUrl: String?,
        posterUrl: String? = null,
        source: String = ""
    ): String? {
        val trimmed = backdropUrl?.trim()?.takeIf { it.isNotBlank() } ?: return null
        if (ShikimoriPosterResolver.isMissingImage(trimmed)) return null

        var sanitized = if (trimmed.startsWith("//")) "https:$trimmed" else trimmed

        // Bangumi API'nin /pic/cover/ görselleri her zaman dikey kapaktır, yatay backdrop değildir.
        // Check the image URL, not the item's provider. A Bangumi item can have a
        // perfectly valid TMDB/Fanart backdrop after cross-source artwork resolution.
        if (sanitized.contains("bgm.tv/") && sanitized.contains("/pic/cover/")) {
            return null
        }

        // Kitsu poster_images veya Simkl /posters/ dikeydir.
        if (sanitized.contains("/poster_images/") || sanitized.contains("simkl.in/posters/")) {
            return null
        }

        // AniList/MAL gibi kaynakların dikey kapak adresleri yatay arka plan sayılmaz
        // (ör. `s4.anilist.co/.../cover/large/bx123.jpg`). Aksi hâlde 16:9 sinematik bantta
        // yanlarda dolgu şeritleri oluşur.
        if (isLikelyVerticalUrl(url = sanitized, posterUrl = null, backdropUrl = null)) {
            return null
        }

        // Poster ile aynı görsel kimliğine sahipse yatay backdrop olarak kullanılmaz.
        if (!posterUrl.isNullOrBlank() && normalizeImageIdentity(sanitized) == normalizeImageIdentity(posterUrl)) {
            return null
        }

        // TMDB backdrop boyutunu /original'a yükselt.
        if (sanitized.contains("image.tmdb.org/t/p/")) {
            sanitized = sanitized.replace(
                Regex("https?://image\\.tmdb\\.org/t/p/(?:w\\d+|h\\d+)/"),
                "https://image.tmdb.org/t/p/original/"
            )
        }

        // Kitsu cover_images boyutunu original'a yükselt.
        if (sanitized.contains("kitsu") && sanitized.contains("/cover_images/")) {
            sanitized = sanitized.replace(
                Regex("(/cover_images/\\d+/)(?:tiny|small|medium|large)(\\.[a-zA-Z0-9]+)"),
                "$1original$2"
            )
        }

        return sanitized
    }

    /**
     * URL'nin dikey poster olma olasılığını kontrol eder.
     */
    fun isLikelyVerticalUrl(
        url: String?,
        posterUrl: String?,
        backdropUrl: String?
    ): Boolean {
        if (url.isNullOrBlank()) return false
        if (!posterUrl.isNullOrBlank() && normalizeImageIdentity(url) == normalizeImageIdentity(posterUrl)) {
            if (backdropUrl.isNullOrBlank() || normalizeImageIdentity(url) != normalizeImageIdentity(backdropUrl)) {
                return true
            }
        }
        val lower = url.lowercase()
        return lower.contains("/pic/cover/") ||
            lower.contains("/poster_images/") ||
            lower.contains("simkl.in/posters/") ||
            lower.contains("/bx") && lower.contains("anilist.co") && lower.contains("/cover/") ||
            lower.contains("myanimelist.net/images/anime/") ||
            lower.contains("myanimelist.net/images/manga/")
    }

    internal fun normalizeImageIdentity(url: String): String =
        url.trim()
            .lowercase()
            .replace(Regex("^https?://"), "")
            .replace(Regex("lain\\.bgm\\.tv/r/\\d+/pic/cover/[a-z]/"), "lain.bgm.tv/pic/cover/")
            .replace(Regex("lain\\.bgm\\.tv/pic/cover/[a-z]/"), "lain.bgm.tv/pic/cover/")
            .replace(Regex("image\\.tmdb\\.org/t/p/(?:original|w\\d+|h\\d+)/"), "image.tmdb.org/t/p/")

    /**
     * Dikey poster adayını çözünürlük, 2:3 en-boy oranı, dil tercihi ve topluluk puanına göre puanlar.
     */
    internal fun scorePosterCandidate(
        candidate: ArtworkCandidate,
        preferredLanguage: String,
        targetSeason: Int? = null
    ): Double {
        if (candidate.isBackdrop) return -1_000.0
        if (candidate.width > 0 && candidate.height > 0 && candidate.width >= candidate.height) {
            // Yatay veya kare görsel dikey poster olamaz.
            return -1_000.0
        }

        var score = 0.0

        // 1. Çözünürlük kalitesi
        score += when {
            candidate.width >= 2000 || candidate.height >= 2800 -> 38.0
            candidate.width >= 1400 || candidate.height >= 2000 -> 30.0
            candidate.width >= 1000 || candidate.height >= 1400 -> 24.0
            candidate.provider.equals("Fanart.tv", ignoreCase = true) -> 25.0 // Fanart tvposter/movieposter 1000x1426 standardındadır
            candidate.width >= 680 || candidate.height >= 960 -> 14.0
            else -> 6.0
        }

        // 2. Dikey poster en-boy oranı (klasik 2:3 ≈ 0.667)
        if (candidate.width > 0 && candidate.height > 0) {
            val aspect = candidate.width.toDouble() / candidate.height.toDouble()
            score += when {
                aspect in 0.62..0.74 -> 10.0
                aspect in 0.56..0.82 -> 5.0
                else -> 0.0
            }
        }

        // 3. Dil tercihi
        val langRank = LogoSelectionPolicy.languageRank(candidate.language, preferredLanguage)
        score += when (langRank) {
            0 -> 24.0 // Kullanıcının tercih ettiği dil
            1 -> 20.0 // İngilizce
            2 -> 18.0 // Yazısız (textless / key art)
            3 -> 14.0 // Orijinal Japonca / Çince
            else -> 4.0
        }

        // 4. Sezon uyumu
        val wantedSeason = targetSeason?.takeIf { it > 1 }?.toString()
        val candidateSeason = candidate.season?.trim()?.takeIf { it.isNotBlank() && it != "all" }
        score += when {
            wantedSeason != null && candidateSeason == wantedSeason -> 14.0
            wantedSeason == null && candidateSeason == null -> 4.0
            wantedSeason == null && candidateSeason != null -> -8.0
            else -> 0.0
        }

        // 5. Sağlayıcı topluluk oyları (TMDB voteAverage/voteCount ve Fanart likes)
        score += if (candidate.provider.equals("Fanart.tv", ignoreCase = true)) {
            14.0 + ln(candidate.likes.coerceAtLeast(0) + 1.0) * 6.5
        } else {
            candidate.voteAverage.coerceIn(0.0, 10.0) * 2.2 +
                ln(candidate.voteCount.coerceAtLeast(0) + 1.0) * 4.5
        }

        return score
    }

    /**
     * Yatay arka plan (backdrop) adayını çözünürlük (4K/2K/1080p), 16:9 en-boy oranı,
     * yazısız (textless) temizlik ve topluluk puanına göre puanlar.
     */
    internal fun scoreBackdropCandidate(
        candidate: ArtworkCandidate,
        preferredLanguage: String,
        targetSeason: Int? = null
    ): Double {
        if (!candidate.isBackdrop) return -1_000.0
        if (candidate.width > 0 && candidate.height > 0 && candidate.width < candidate.height * 1.25) {
            // Dikey veya kareye yakın görsel yatay arka plan olamaz.
            return -1_000.0
        }

        var score = 0.0

        // 1. Çözünürlük kalitesi (4K > 2K > 1080p Full HD > 720p)
        score += when {
            candidate.width >= 3840 || candidate.height >= 2160 -> 42.0
            candidate.width >= 2560 || candidate.height >= 1440 -> 34.0
            candidate.width >= 1920 || candidate.height >= 1080 -> 28.0
            candidate.provider.equals("Fanart.tv", ignoreCase = true) -> 29.0 // Fanart showbackground/moviebackground 1080p/4K standardındadır
            candidate.width >= 1280 || candidate.height >= 720 -> 15.0
            else -> 4.0
        }

        // 2. 16:9 yatay sinematik oran (≈ 1.777)
        if (candidate.width > 0 && candidate.height > 0) {
            val aspect = candidate.width.toDouble() / candidate.height.toDouble()
            score += when {
                aspect in 1.68..1.88 -> 12.0
                aspect in 1.45..2.25 -> 5.0
                else -> -6.0
            }
        }

        // 3. Dil / Yazısız temizlik (Vitrin başlığı/logosuyla çakışmaması için yazısız arka plan en değerlidir)
        val lang = candidate.language?.trim()?.lowercase().orEmpty()
        val isTextless = lang.isEmpty() || lang == "00" || lang == "xx" || lang == "null" || lang == "und"
        val langRank = LogoSelectionPolicy.languageRank(candidate.language, preferredLanguage)
        score += when {
            isTextless -> 26.0
            langRank == 0 -> 20.0
            langRank == 1 -> 16.0
            langRank == 3 -> 10.0
            else -> 3.0
        }

        // 4. Sezon uyumu
        val wantedSeason = targetSeason?.takeIf { it > 1 }?.toString()
        val candidateSeason = candidate.season?.trim()?.takeIf { it.isNotBlank() && it != "all" }
        score += when {
            wantedSeason != null && candidateSeason == wantedSeason -> 12.0
            wantedSeason == null && candidateSeason == null -> 4.0
            wantedSeason == null && candidateSeason != null -> -6.0
            else -> 0.0
        }

        // 5. Sağlayıcı topluluk oyları
        score += if (candidate.provider.equals("Fanart.tv", ignoreCase = true)) {
            16.0 + ln(candidate.likes.coerceAtLeast(0) + 1.0) * 7.0
        } else {
            candidate.voteAverage.coerceIn(0.0, 10.0) * 2.4 +
                ln(candidate.voteCount.coerceAtLeast(0) + 1.0) * 4.8
        }

        return score
    }

    internal fun selectBestPosterCandidates(
        candidates: List<ArtworkCandidate>,
        preferredLanguage: String,
        fallbackPosterUrl: String?,
        targetSeason: Int? = null
    ): List<String> {
        val ranked = candidates
            .map { it to scorePosterCandidate(it, preferredLanguage, targetSeason) }
            .filter { it.second > -500.0 }
            .sortedByDescending { it.second }
            .map { it.first.url.trim() }
            .filter { it.isNotBlank() }

        val combined = buildList {
            addAll(ranked)
            if (!fallbackPosterUrl.isNullOrBlank()) {
                add(fallbackPosterUrl.trim())
            }
        }
        return deduplicateUrls(combined)
    }

    internal fun selectBestBackdropCandidates(
        candidates: List<ArtworkCandidate>,
        preferredLanguage: String,
        fallbackBackdropUrl: String?,
        targetSeason: Int? = null
    ): List<String> {
        val ranked = candidates
            .map { it to scoreBackdropCandidate(it, preferredLanguage, targetSeason) }
            .filter { it.second > -500.0 }
            .sortedByDescending { it.second }
            .map { it.first.url.trim() }
            .filter { it.isNotBlank() }

        val combined = buildList {
            addAll(ranked)
            if (!fallbackBackdropUrl.isNullOrBlank()) {
                add(fallbackBackdropUrl.trim())
            }
        }
        return deduplicateUrls(combined)
    }

    private fun deduplicateUrls(urls: List<String>): List<String> {
        val seen = HashSet<String>(urls.size)
        val out = ArrayList<String>(urls.size)
        for (url in urls) {
            val key = normalizeImageIdentity(url)
            if (seen.add(key)) {
                out.add(url)
            }
        }
        return out
    }

    /**
     * Bir vitrin öğesi için TMDB + Fanart.tv üzerinden en kaliteli dikey poster ve
     * en kaliteli yatay arka planı çözer.
     */
    suspend fun resolveArtwork(item: JikanSearchResult): HeroArtwork = withContext(Dispatchers.IO) {
        val key = cacheKey(item)
        cache[key]?.let { return@withContext it }

        val deferred = inFlightMutex.withLock {
            cache[key]?.let { return@withContext it }
            inFlight[key] ?: resolverScope.async {
                try {
                    computeArtworkInternal(item).also { resolved ->
                        cache[key] = resolved
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "resolveArtwork failed for ${item.title} (${item.source}): ${e.message}")
                    buildFallbackArtwork(item).also { fallback ->
                        cache[key] = fallback
                    }
                } finally {
                    inFlightMutex.withLock { inFlight.remove(key) }
                }
            }.also { created ->
                inFlight[key] = created
            }
        }

        deferred.await()
    }

    private suspend fun buildFallbackArtwork(item: JikanSearchResult): HeroArtwork {
        var nativePoster = upgradeNativePosterUrl(item.imageUrl, item.source)
        if (item.source.equals("shikimori", ignoreCase = true) || item.source.equals("shiki", ignoreCase = true)) {
            val kind = if (item.type == MediaType.Manga) ShikimoriPosterResolver.Kind.MANGA else ShikimoriPosterResolver.Kind.ANIME
            val fullShiki = runCatching {
                ShikimoriPosterResolver.posterFor(kind, item.malId)?.fullUrl
            }.getOrNull()
            if (!fullShiki.isNullOrBlank()) {
                nativePoster = fullShiki
            }
        }
        val nativeBackdrop = sanitizeNativeBackdropUrl(item.backdropUrl, nativePoster, item.source)
        return HeroArtwork(
            bestPosterUrl = nativePoster,
            bestBackdropUrl = nativeBackdrop,
            posterCandidates = listOfNotNull(nativePoster),
            backdropCandidates = listOfNotNull(nativeBackdrop),
            resolvedTmdbId = item.tmdbId?.takeIf { it > 0 },
            resolvedMalId = item.realMalId?.takeIf { it > 0 },
            isMovie = item.type == MediaType.Movie
        )
    }

    private suspend fun computeArtworkInternal(item: JikanSearchResult): HeroArtwork = coroutineScope {
        // 1. Kaynağın kendi poster/backdrop URL'lerini yükselt ve temizle.
        var nativePoster = upgradeNativePosterUrl(item.imageUrl, item.source)
        val canonicalSource = MalJikanMediaSupport.canonicalSource(item.source).let {
            if (it == "bgm") "bangumi" else it
        }

        if (canonicalSource == "shikimori" && item.malId > 0) {
            val kind = if (item.type == MediaType.Manga) ShikimoriPosterResolver.Kind.MANGA else ShikimoriPosterResolver.Kind.ANIME
            val shikiFull = runCatching {
                ShikimoriPosterResolver.posterFor(kind, item.malId)?.fullUrl
            }.getOrNull()
            if (!shikiFull.isNullOrBlank()) {
                nativePoster = shikiFull
            }
        }

        val nativeBackdrop = sanitizeNativeBackdropUrl(item.backdropUrl, nativePoster, item.source)

        // Manga öğeleri yalnızca dikey kapak kullanır; TMDB/Fanart film/dizi araması yapılmaz.
        if (item.type == MediaType.Manga) {
            return@coroutineScope HeroArtwork(
                bestPosterUrl = nativePoster,
                bestBackdropUrl = null,
                posterCandidates = listOfNotNull(nativePoster),
                backdropCandidates = emptyList(),
                isMovie = false
            )
        }

        val preferredLang = KitsugiEpisodeRatingsRepository.normalizeLanguageCode(
            KitsugiEpisodeRatingsRepository.getPreferredImageLanguage()
        )
        val tmdbAllowed = KitsugiEpisodeRatingsRepository.isTmdbArtworkEnabled()
        val (fanartAllowed, _) = KitsugiEpisodeRatingsRepository.getFanartSettings()

        if (!tmdbAllowed && !fanartAllowed) {
            return@coroutineScope HeroArtwork(
                bestPosterUrl = nativePoster,
                bestBackdropUrl = nativeBackdrop,
                posterCandidates = listOfNotNull(nativePoster),
                backdropCandidates = listOfNotNull(nativeBackdrop),
                resolvedTmdbId = item.tmdbId?.takeIf { it > 0 },
                resolvedMalId = item.realMalId?.takeIf { it > 0 },
                isMovie = item.type == MediaType.Movie
            )
        }

        val allTitles = mutableListOf<String>().apply {
            item.titleEnglish?.trim()?.takeIf { it.isNotBlank() }?.let { add(it) }
            item.title.trim().takeIf { it.isNotBlank() }?.let { add(it) }
            item.titleRomaji?.trim()?.takeIf { it.isNotBlank() }?.let { add(it) }
            item.titleJapanese?.trim()?.takeIf { it.isNotBlank() }?.let { add(it) }
        }.distinct().toMutableList()

        var likelyMovie = item.type == MediaType.Movie ||
            KitsugiEpisodeRatingsRepository.prefersMovieLogo(
                type = item.type,
                subtitle = item.subtitle,
                title = allTitles.joinToString(" | ")
            )

        val targetSeason = KitsugiEpisodeRatingsRepository.determineTargetSeason(
            tmdbSeason = null,
            title = item.title,
            titleEnglish = item.titleEnglish,
            synonyms = listOfNotNull(item.titleRomaji, item.titleJapanese)
        ).takeIf { it > 1 }

        // 2. Kaynağa göre çapraz kimlikleri (TMDB / MAL / AniList / Kitsu / TVDB) çöz.
        var malId: Int? = item.realMalId?.takeIf { it in 1..99_999_999 }
        var aniListId: Int? = null
        var kitsuId: Int? = null
        var tvdbId: Int? = null
        var tmdbId: Int? = item.tmdbId?.takeIf { it > 0 }

        when (canonicalSource) {
            "tmdb" -> {
                if (tmdbId == null && item.malId > 0) {
                    tmdbId = item.malId
                }
            }
            "jikan" -> {
                if (malId == null && item.malId in 1..99_999_999) {
                    malId = item.malId
                }
                if (tmdbId == null && malId != null) {
                    tmdbId = KitsugiEpisodeRatingsRepository.resolveTmdbIdFromMal(malId)
                }
            }
            "anilist" -> {
                aniListId = when (item.malId) {
                    in 100_000_001..199_999_999 -> item.malId - 100_000_000
                    in 1..99_999_999 -> item.malId
                    else -> null
                }
                if (tmdbId == null && aniListId != null) {
                    tmdbId = KitsugiEpisodeRatingsRepository.resolveTmdbIdFromAniList(aniListId)
                }
                if (tmdbId == null && malId != null) {
                    tmdbId = KitsugiEpisodeRatingsRepository.resolveTmdbIdFromMal(malId)
                }
            }
            "kitsu" -> {
                kitsuId = when (item.malId) {
                    in 300_000_001..399_999_999 -> item.malId - 300_000_000
                    in 1..99_999_999 -> item.malId
                    else -> null
                }
                if (!likelyMovie && kitsuId != null) {
                    likelyMovie = KitsugiEpisodeRatingsRepository.isKitsuMovieId(kitsuId)
                }
                if (tmdbId == null && kitsuId != null) {
                    tmdbId = KitsugiEpisodeRatingsRepository.resolveTmdbIdFromKitsu(kitsuId)
                }
                if (tmdbId == null && malId != null) {
                    tmdbId = KitsugiEpisodeRatingsRepository.resolveTmdbIdFromMal(malId)
                }
            }
            "shikimori" -> {
                if (malId == null && item.malId > 0) {
                    malId = KitsugiIdResolver.resolveMalIdFromShikimori(item.malId)
                }
                if (tmdbId == null && malId != null) {
                    tmdbId = KitsugiEpisodeRatingsRepository.resolveTmdbIdFromMal(malId)
                }
            }
            "simkl" -> {
                if (item.malId > 0 && (tmdbId == null || malId == null)) {
                    val cross = runCatching {
                        KitsugiSimklDetailClient.resolveSimklCrossIds(
                            simklId = item.malId,
                            mediaType = item.type,
                            hintTmdbId = tmdbId,
                            hintMalId = malId
                        )
                    }.getOrNull()
                    if (cross != null) {
                        malId = malId ?: cross.malId?.takeIf { it in 1..99_999_999 }
                        aniListId = aniListId ?: cross.aniListId?.takeIf { it > 0 }
                        kitsuId = kitsuId ?: cross.kitsuId?.takeIf { it > 0 }
                        tmdbId = tmdbId ?: cross.tmdbId?.takeIf { it > 0 }
                        if (cross.isMovie) likelyMovie = true
                        cross.title?.trim()?.takeIf { it.isNotBlank() && it !in allTitles }?.let { allTitles.add(0, it) }
                    }
                }
                if (tmdbId == null && malId != null) {
                    tmdbId = KitsugiEpisodeRatingsRepository.resolveTmdbIdFromMal(malId)
                }
                if (tmdbId == null && aniListId != null) {
                    tmdbId = KitsugiEpisodeRatingsRepository.resolveTmdbIdFromAniList(aniListId)
                }
                if (tmdbId == null && kitsuId != null) {
                    tmdbId = KitsugiEpisodeRatingsRepository.resolveTmdbIdFromKitsu(kitsuId)
                }
            }
            "bangumi" -> {
                // Bangumi için önce hızlı TMDB başlık/yıl araması deneriz; eşleşmezse Bangumi çapraz ID çözümleyicisine düşeriz.
                if (tmdbId == null && malId != null) {
                    tmdbId = KitsugiEpisodeRatingsRepository.resolveTmdbIdFromMal(malId)
                }
            }
            else -> {
                if (tmdbId == null && malId != null) {
                    tmdbId = KitsugiEpisodeRatingsRepository.resolveTmdbIdFromMal(malId)
                }
            }
        }

        // 3. Eğer doğrudan ID üzerinden TMDB bulunamadıysa, TMDB başlık + yıl araması yap.
        var searchMatch: TmdbSearchMatch? = null
        if (tmdbId == null && allTitles.isNotEmpty()) {
            searchMatch = searchTmdbMatch(
                titles = allTitles,
                mediaType = item.type,
                likelyMovie = likelyMovie,
                year = item.year
            )
            if (searchMatch != null) {
                tmdbId = searchMatch.tmdbId
                likelyMovie = searchMatch.isMovie
            }
        }

        // 4. Bangumi öğesi hâlâ çözülemediyse (ör. yalnızca Çince başlık varsa), Bangumi subject + infobox çapraz çözümleyicisini çalıştır.
        if (tmdbId == null && canonicalSource == "bangumi") {
            val cross = runCatching {
                KitsugiBangumiDetailClient.resolveCrossIds(item.malId, item.type)
            }.getOrNull()
            if (cross != null) {
                malId = malId ?: cross.malId
                aniListId = aniListId ?: cross.aniListId
                kitsuId = kitsuId ?: cross.kitsuId
                tvdbId = tvdbId ?: cross.tvdbId
                tmdbId = cross.tmdbId
                    ?: malId?.let { KitsugiEpisodeRatingsRepository.resolveTmdbIdFromMal(it) }
                    ?: aniListId?.let { KitsugiEpisodeRatingsRepository.resolveTmdbIdFromAniList(it) }
                    ?: kitsuId?.let { KitsugiEpisodeRatingsRepository.resolveTmdbIdFromKitsu(it) }
            }
        }

        if (!likelyMovie && tmdbId != null && canonicalSource != "tmdb") {
            if (DetailCache.tmdbMediaCache[tmdbId] == "movie") {
                likelyMovie = true
            }
        }

        // 5. TMDB ve Fanart.tv görsellerini paralel olarak çek.
        val tmdbBundleDeferred = async {
            if (!tmdbAllowed) return@async null
            var bundle = tmdbId?.let { id ->
                fetchTmdbArtworkBundle(
                    tmdbId = id,
                    isMovie = likelyMovie,
                    expectedTitles = if (canonicalSource == "tmdb" || searchMatch?.tmdbId == id) emptyList() else allTitles,
                    preferredLanguage = preferredLang,
                    targetSeason = targetSeason
                )
            }
            // ARM yanlış tür veya eski ID döndürdüyse başlık aramasıyla doğrula
            if (bundle == null && allTitles.isNotEmpty()) {
                val fallbackMatch = searchMatch ?: searchTmdbMatch(
                    titles = allTitles,
                    mediaType = item.type,
                    likelyMovie = likelyMovie,
                    year = item.year
                )
                if (fallbackMatch != null) {
                    tmdbId = fallbackMatch.tmdbId
                    likelyMovie = fallbackMatch.isMovie
                    bundle = fetchTmdbArtworkBundle(
                        tmdbId = fallbackMatch.tmdbId,
                        isMovie = fallbackMatch.isMovie,
                        expectedTitles = emptyList(),
                        preferredLanguage = preferredLang,
                        targetSeason = targetSeason
                    )
                    if (bundle == null) {
                        val fallbackPosters = listOfNotNull(
                            fallbackMatch.posterPath?.let {
                                ArtworkCandidate(
                                    url = "$TMDB_IMAGE_ORIGINAL$it",
                                    provider = "TMDB",
                                    isBackdrop = false,
                                    width = 1000,
                                    height = 1500,
                                    language = preferredLang
                                )
                            }
                        )
                        val fallbackBackdrops = listOfNotNull(
                            fallbackMatch.backdropPath?.let {
                                ArtworkCandidate(
                                    url = "$TMDB_IMAGE_ORIGINAL$it",
                                    provider = "TMDB",
                                    isBackdrop = true,
                                    width = 1920,
                                    height = 1080,
                                    language = null
                                )
                            }
                        )
                        bundle = TmdbResolvedBundle(
                            tmdbId = fallbackMatch.tmdbId,
                            isMovie = fallbackMatch.isMovie,
                            tvdbId = null,
                            posters = fallbackPosters,
                            backdrops = fallbackBackdrops
                        )
                    }
                }
            }
            bundle
        }

        val tmdbBundle = tmdbBundleDeferred.await()
        val resolvedTmdbId = tmdbBundle?.tmdbId ?: tmdbId
        val resolvedIsMovie = tmdbBundle?.isMovie ?: likelyMovie
        val resolvedTvdbId = tmdbBundle?.tvdbId ?: tvdbId

        if (resolvedTmdbId != null && resolvedTvdbId != null && !resolvedIsMovie) {
            DetailCache.tmdbToTvdbCache.putIfAbsent(resolvedTmdbId, resolvedTvdbId)
        }

        val fanartItems = if (fanartAllowed && (resolvedTmdbId != null || malId != null || aniListId != null || kitsuId != null)) {
            runCatching {
                KitsugiEpisodeRatingsRepository.getFanartGalleryItems(
                    tmdbId = resolvedTmdbId ?: 0,
                    isMovie = resolvedIsMovie,
                    fallbackMalId = malId,
                    fallbackAniListId = aniListId,
                    fallbackKitsuId = kitsuId
                )
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

        val fanartPosters = fanartItems
            .asSequence()
            .filter { it.category == GalleryCategory.POSTER && it.url.isNotBlank() }
            .map {
                ArtworkCandidate(
                    url = it.url,
                    provider = "Fanart.tv",
                    isBackdrop = false,
                    width = it.width ?: 1000,
                    height = it.height ?: 1426,
                    language = it.language,
                    likes = it.details["likes"]?.toIntOrNull() ?: 0,
                    season = it.season
                )
            }
            .toList()

        val fanartBackdrops = fanartItems
            .asSequence()
            .filter { it.category == GalleryCategory.BACKDROP && it.url.isNotBlank() }
            .map {
                ArtworkCandidate(
                    url = it.url,
                    provider = "Fanart.tv",
                    isBackdrop = true,
                    width = it.width ?: 1920,
                    height = it.height ?: 1080,
                    language = it.language,
                    likes = it.details["likes"]?.toIntOrNull() ?: 0,
                    season = it.season
                )
            }
            .toList()

        val allPosterCandidates = (tmdbBundle?.posters.orEmpty() + fanartPosters)
        val allBackdropCandidates = (tmdbBundle?.backdrops.orEmpty() + fanartBackdrops)

        val rankedPosters = selectBestPosterCandidates(
            candidates = allPosterCandidates,
            preferredLanguage = preferredLang,
            fallbackPosterUrl = nativePoster,
            targetSeason = targetSeason
        )
        val rankedBackdrops = selectBestBackdropCandidates(
            candidates = allBackdropCandidates,
            preferredLanguage = preferredLang,
            fallbackBackdropUrl = nativeBackdrop,
            targetSeason = targetSeason
        )

        HeroArtwork(
            bestPosterUrl = rankedPosters.firstOrNull() ?: nativePoster,
            bestBackdropUrl = rankedBackdrops.firstOrNull() ?: nativeBackdrop,
            posterCandidates = rankedPosters,
            backdropCandidates = rankedBackdrops,
            resolvedTmdbId = resolvedTmdbId,
            resolvedMalId = malId,
            resolvedTvdbId = resolvedTvdbId,
            isMovie = resolvedIsMovie
        )
    }

    /**
     * TMDB `/3/{tv|movie}/{id}?append_to_response=images,external_ids,alternative_titles`
     * uç noktasından tüm poster ve backdrop adaylarını (`vote_average`, `vote_count`, `width`, `height`, `iso_639_1`)
     * tam `/original` çözünürlükte çeker.
     */
    private suspend fun fetchTmdbArtworkBundle(
        tmdbId: Int,
        isMovie: Boolean,
        expectedTitles: List<String>,
        preferredLanguage: String,
        targetSeason: Int?
    ): TmdbResolvedBundle? {
        if (tmdbId <= 0) return null
        val apiKey = TmdbApiClient.getActiveApiKey()
        if (apiKey.isBlank()) return null

        val includeLanguages = LogoSelectionPolicy.tmdbImageLanguages(preferredLanguage).joinToString(",")
        val typesToTry = if (isMovie) listOf("movie", "tv") else listOf("tv", "movie")

        for (type in typesToTry) {
            val isMovieType = type == "movie"
            // Eğer beklenen başlık yoksa yalnızca birincil türü dene (movie/tv ID çakışmasını önlemek için)
            if (expectedTitles.isEmpty() && isMovieType != isMovie) continue

            val json = runCatching {
                val url = URL(
                    "$TMDB_BASE_URL/$type/$tmdbId" +
                        "?api_key=$apiKey" +
                        "&append_to_response=images,external_ids,alternative_titles" +
                        "&include_image_language=$includeLanguages"
                )
                val body = KitsugiApiBase.executeGetRequest(url)
                if (body.isNullOrBlank()) null else JSONObject(body)
            }.getOrNull() ?: continue

            val entryTitle = if (isMovieType) json.optString("title") else json.optString("name")
            val entryOriginal = if (isMovieType) json.optString("original_title") else json.optString("original_name")
            val altTitles = TmdbArtworkIdentity.extractAlternativeTitles(json)

            if (expectedTitles.isNotEmpty() &&
                !TmdbArtworkIdentity.matches(expectedTitles, entryTitle, entryOriginal, altTitles)
            ) {
                val genresArr = json.optJSONArray("genres")
                val hasAnimation = genresArr != null && (0 until genresArr.length()).any {
                    genresArr.optJSONObject(it)?.optInt("id") == 16
                }
                val isRomajiOnlyAnimeFallback = isMovieType == isMovie &&
                    hasAnimation &&
                    com.kitsugi.animelist.utils.MediaTitleResolver.hasCjk(entryOriginal) &&
                    expectedTitles.none { com.kitsugi.animelist.utils.MediaTitleResolver.hasCjk(it) }
                if (!isRomajiOnlyAnimeFallback) {
                    continue
                }
            }

            DetailCache.tmdbMediaCache[tmdbId] = type
            val tvdbId = if (!isMovieType) {
                json.optJSONObject("external_ids")?.optInt("tvdb_id", 0)?.takeIf { it > 0 }
            } else null

            val imagesObj = json.optJSONObject("images")
            val posters = ArrayList<ArtworkCandidate>()
            val backdrops = ArrayList<ArtworkCandidate>()

            // Sezon > 1 ise sezon posterlerini de çek
            if (!isMovieType && targetSeason != null && targetSeason > 1) {
                val seasonPosters = fetchTmdbSeasonPosters(tmdbId, targetSeason, apiKey, includeLanguages)
                posters.addAll(seasonPosters)
            }

            val postersArr = imagesObj?.optJSONArray("posters")
            if (postersArr != null) {
                for (i in 0 until postersArr.length()) {
                    val obj = postersArr.optJSONObject(i) ?: continue
                    val path = obj.optString("file_path").trim().takeIf { it.isNotBlank() && it != "null" } ?: continue
                    val lang = obj.optString("iso_639_1").trim().takeIf { it.isNotBlank() && it != "null" }
                    posters.add(
                        ArtworkCandidate(
                            url = "$TMDB_IMAGE_ORIGINAL$path",
                            provider = "TMDB",
                            isBackdrop = false,
                            width = obj.optInt("width", 1000).coerceAtLeast(1000),
                            height = obj.optInt("height", 1500).coerceAtLeast(1500),
                            language = lang,
                            voteAverage = obj.optDouble("vote_average", 0.0).let { if (it.isNaN()) 0.0 else it },
                            voteCount = obj.optInt("vote_count", 0)
                        )
                    )
                }
            }

            val backdropsArr = imagesObj?.optJSONArray("backdrops")
            if (backdropsArr != null) {
                for (i in 0 until backdropsArr.length()) {
                    val obj = backdropsArr.optJSONObject(i) ?: continue
                    val path = obj.optString("file_path").trim().takeIf { it.isNotBlank() && it != "null" } ?: continue
                    val lang = obj.optString("iso_639_1").trim().takeIf { it.isNotBlank() && it != "null" }
                    backdrops.add(
                        ArtworkCandidate(
                            url = "$TMDB_IMAGE_ORIGINAL$path",
                            provider = "TMDB",
                            isBackdrop = true,
                            width = obj.optInt("width", 1920).coerceAtLeast(1280),
                            height = obj.optInt("height", 1080).coerceAtLeast(720),
                            language = lang,
                            voteAverage = obj.optDouble("vote_average", 0.0).let { if (it.isNaN()) 0.0 else it },
                            voteCount = obj.optInt("vote_count", 0)
                        )
                    )
                }
            }

            // Root poster_path / backdrop_path yedekleri
            val rootPoster = json.optString("poster_path").trim().takeIf { it.isNotBlank() && it != "null" }
            if (rootPoster != null) {
                posters.add(
                    ArtworkCandidate(
                        url = "$TMDB_IMAGE_ORIGINAL$rootPoster",
                        provider = "TMDB",
                        isBackdrop = false,
                        width = 1000,
                        height = 1500,
                        language = preferredLanguage,
                        voteAverage = json.optDouble("vote_average", 0.0).let { if (it.isNaN()) 0.0 else it },
                        voteCount = json.optInt("vote_count", 0)
                    )
                )
            }

            val rootBackdrop = json.optString("backdrop_path").trim().takeIf { it.isNotBlank() && it != "null" }
            if (rootBackdrop != null) {
                backdrops.add(
                    ArtworkCandidate(
                        url = "$TMDB_IMAGE_ORIGINAL$rootBackdrop",
                        provider = "TMDB",
                        isBackdrop = true,
                        width = 1920,
                        height = 1080,
                        language = null,
                        voteAverage = json.optDouble("vote_average", 0.0).let { if (it.isNaN()) 0.0 else it },
                        voteCount = json.optInt("vote_count", 0)
                    )
                )
            }

            if (posters.isNotEmpty() || backdrops.isNotEmpty()) {
                return TmdbResolvedBundle(
                    tmdbId = tmdbId,
                    isMovie = isMovieType,
                    tvdbId = tvdbId,
                    posters = posters,
                    backdrops = backdrops
                )
            }
        }
        return null
    }

    private fun fetchTmdbSeasonPosters(
        tmdbId: Int,
        seasonNumber: Int,
        apiKey: String,
        includeLanguages: String
    ): List<ArtworkCandidate> = runCatching {
        val url = URL(
            "$TMDB_BASE_URL/tv/$tmdbId/season/$seasonNumber/images" +
                "?api_key=$apiKey&include_image_language=$includeLanguages"
        )
        val body = KitsugiApiBase.executeGetRequest(url) ?: return@runCatching emptyList()
        val postersArr = JSONObject(body).optJSONArray("posters") ?: return@runCatching emptyList()
        buildList {
            for (i in 0 until postersArr.length()) {
                val obj = postersArr.optJSONObject(i) ?: continue
                val path = obj.optString("file_path").trim().takeIf { it.isNotBlank() && it != "null" } ?: continue
                val lang = obj.optString("iso_639_1").trim().takeIf { it.isNotBlank() && it != "null" }
                add(
                    ArtworkCandidate(
                        url = "$TMDB_IMAGE_ORIGINAL$path",
                        provider = "TMDB",
                        isBackdrop = false,
                        width = obj.optInt("width", 1000).coerceAtLeast(1000),
                        height = obj.optInt("height", 1500).coerceAtLeast(1500),
                        language = lang,
                        voteAverage = obj.optDouble("vote_average", 0.0).let { if (it.isNaN()) 0.0 else it },
                        voteCount = obj.optInt("vote_count", 0),
                        season = seasonNumber.toString()
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    /**
     * Başlık ve yıl kullanarak TMDB'de en uygun dizi veya filmi bulur.
     * Hem tüm bilinen başlıkları (`titleEnglish`, `title`, `titleRomaji`, `titleJapanese`)
     * hem de gerekiyorsa sezon eki temizlenmiş ana başlığı dener.
     */
    private fun searchTmdbMatch(
        titles: List<String>,
        mediaType: MediaType,
        likelyMovie: Boolean,
        year: Int?
    ): TmdbSearchMatch? {
        val apiKey = TmdbApiClient.getActiveApiKey()
        if (apiKey.isBlank()) return null

        val endpoint = when {
            mediaType == MediaType.Movie || likelyMovie -> "movie"
            mediaType == MediaType.TvShow -> "tv"
            else -> "multi"
        }

        val searchQueries = buildList {
            for (t in titles) {
                val cleaned = t.trim()
                if (cleaned.length >= 2) add(cleaned)
                val strippedSeason = stripSeasonSuffix(cleaned)
                if (strippedSeason.length >= 2 && !strippedSeason.equals(cleaned, ignoreCase = true)) {
                    add(strippedSeason)
                }
            }
        }.distinct().take(3)

        var bestMatch: TmdbSearchMatch? = null

        for (queryTitle in searchQueries) {
            val encoded = URLEncoder.encode(queryTitle, "UTF-8")
            val url = "$TMDB_BASE_URL/search/$endpoint?api_key=$apiKey&query=$encoded&include_adult=false"
            val body = runCatching { KitsugiApiBase.executeGetRequest(URL(url)) }.getOrNull() ?: continue
            val results = runCatching { JSONObject(body).optJSONArray("results") }.getOrNull() ?: continue

            for (i in 0 until minOf(results.length(), 10)) {
                val r = results.optJSONObject(i) ?: continue
                val mediaTypeRaw = r.optString("media_type", endpoint)
                if (mediaTypeRaw == "person") continue

                val id = r.optInt("id", 0).takeIf { it > 0 } ?: continue
                val isMovieCandidate = mediaTypeRaw == "movie" || (endpoint == "movie" && mediaTypeRaw.isBlank())

                val candidateNames = listOfNotNull(
                    r.optString("title", "").takeIf { it.isNotBlank() },
                    r.optString("name", "").takeIf { it.isNotBlank() },
                    r.optString("original_title", "").takeIf { it.isNotBlank() },
                    r.optString("original_name", "").takeIf { it.isNotBlank() }
                )

                val directTitleScore = titles.maxOfOrNull { expected ->
                    val strippedExpected = stripSeasonSuffix(expected)
                    candidateNames.maxOfOrNull { cand ->
                        maxOf(
                            TmdbDiscoverClient.backdropTitleMatchScore(expected, cand),
                            TmdbDiscoverClient.backdropTitleMatchScore(strippedExpected, cand)
                        )
                    } ?: 0
                } ?: 0

                val releaseDate = r.optString("release_date", "").ifBlank { r.optString("first_air_date", "") }
                val candYear = releaseDate.take(4).toIntOrNull()
                val yearDiff = if (year != null && candYear != null) abs(year - candYear) else null

                // TMDB arama motoru alternatif başlıkları (Romaji/CJK) sunucu tarafında indeksler.
                // Eğer ilk sıradaki sonuç yıl ve tür (ör. Animasyon=16) açısından tam uyuşuyorsa,
                // Romaji <-> İngilizce başlık farkı yüzünden reddedilmemelidir.
                val genreIds = r.optJSONArray("genre_ids")
                var hasAnimationGenre = false
                if (genreIds != null) {
                    for (g in 0 until genreIds.length()) {
                        if (genreIds.optInt(g) == 16) {
                            hasAnimationGenre = true
                            break
                        }
                    }
                }

                val effectiveTitleScore = when {
                    directTitleScore >= 55 -> directTitleScore
                    i == 0 && yearDiff != null && yearDiff <= 1 && (mediaType != MediaType.Anime || hasAnimationGenre) -> 72
                    i <= 1 && yearDiff == 0 && hasAnimationGenre && mediaType == MediaType.Anime -> 68
                    i == 0 && !isMovieCandidate && hasAnimationGenre && mediaType == MediaType.Anime -> 68
                    else -> directTitleScore
                }

                if (effectiveTitleScore < 55) continue
                val isMultiSeasonTvCandidate = !isMovieCandidate &&
                    effectiveTitleScore >= 68 &&
                    (mediaType != MediaType.Anime || hasAnimationGenre)
                if (yearDiff != null && yearDiff > 3 && effectiveTitleScore < 95 && !isMultiSeasonTvCandidate) continue

                val yearBonus = when {
                    yearDiff == null -> 0
                    yearDiff == 0 -> 30
                    yearDiff == 1 -> 15
                    yearDiff == 2 -> 5
                    isMultiSeasonTvCandidate -> 0
                    else -> -25
                }
                val typeBonus = when {
                    mediaType == MediaType.Movie && isMovieCandidate -> 15
                    mediaType == MediaType.TvShow && !isMovieCandidate -> 15
                    mediaType == MediaType.Anime && !likelyMovie && !isMovieCandidate -> 12
                    mediaType == MediaType.Anime && likelyMovie && isMovieCandidate -> 12
                    else -> 0
                }
                val animBonus = if (mediaType == MediaType.Anime && hasAnimationGenre) 12 else 0

                val posterPath = r.optString("poster_path", "").trim().takeIf { it.isNotBlank() && it != "null" }
                val backdropPath = r.optString("backdrop_path", "").trim().takeIf { it.isNotBlank() && it != "null" }
                val artworkBonus = (if (backdropPath != null) 8 else 0) + (if (posterPath != null) 4 else 0)

                val totalScore = effectiveTitleScore + yearBonus + typeBonus + animBonus + artworkBonus
                if (bestMatch == null || totalScore > bestMatch.score) {
                    bestMatch = TmdbSearchMatch(
                        tmdbId = id,
                        isMovie = isMovieCandidate,
                        posterPath = posterPath,
                        backdropPath = backdropPath,
                        score = totalScore
                    )
                }
            }

            if (bestMatch != null && bestMatch.score >= 105) {
                break
            }
        }

        return bestMatch
    }

    internal fun stripSeasonSuffix(title: String): String =
        title
            .replace(Regex("(?i)\\s*[:\\-–]?\\s*(?:season|sezon|part|cour)\\s*\\d+.*$"), "")
            .replace(Regex("(?i)\\s+\\d+(?:st|nd|rd|th)\\s+season.*$"), "")
            .replace(Regex("(?i)\\s+(?:II|III|IV|V)$"), "")
            .replace(Regex("(?i)\\s*第[一二三四五六七八九十0-9]+[期季部].*$"), "")
            .trim()
}
