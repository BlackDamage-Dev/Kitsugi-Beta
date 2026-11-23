package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.core.memory.BoundedCache

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.utils.*
import java.util.concurrent.ConcurrentHashMap

/**
 * TMDB keşfet (trending/popular/top-rated) ve backdrop arama işlevleri.
 * 60 dakikalık in-memory TTL cache ile rate limit baskısını azaltır.
 *
 * Başlık kuralı: yerelleştirilmiş (Türkçe) → İngilizce → Romaji. CJK başlıklar
 * yalnızca hiçbir Latin alternatif yoksa gösterilir. Bkz. [MediaTitleResolver].
 *
 * [TmdbApiClient] tarafından delegate olarak kullanılır; doğrudan çağrılmamalıdır.
 */
internal object TmdbDiscoverClient {

    private const val TAG = "TmdbDiscoverClient"

    // ── In-memory TTL cache ─────────────────────────────────────────────────────
    private const val TTL_MS = 60 * 60 * 1_000L
    private data class CacheEntry(val data: List<JikanSearchResult>, val fetchedAt: Long)
    private val cache = BoundedCache<String, CacheEntry>("tmdb.discover", 60)

    /**
     * Başarısız (boş dönen) sayfalar kısa süre "başarı" gibi önbellekleniyordu: TMDB hız
     * sınırına takılan bir sayfa 1 saat boyunca boş servis ediliyor, "Tümünü Gör" sayfası
     * kaydırdıkça yeni veri getiremiyordu. Artık boş sonuçlar yalnız [EMPTY_TTL_MS] kadar
     * tutulur; sonraki deneme kaynağa yeniden gider.
     */
    private const val EMPTY_TTL_MS = 60 * 1_000L

    /** TMDB'nin sayfa boyutu; her uç 20 öğe döner. */
    private const val TMDB_PAGE_SIZE = 20

    /** Boş sonuç kısa TTL ile, dolu sonuç normal TTL ile taze sayılır. */
    private fun CacheEntry.isFresh(): Boolean {
        val ttl = if (data.isEmpty()) EMPTY_TTL_MS else TTL_MS
        return System.currentTimeMillis() - fetchedAt < ttl
    }

    /**
     * Önbellek anahtarı; dil ve başlık çözümleme sürümünü içerir.
     * Böylece hem dil değişiminde hem de başlık mantığı güncellendiğinde eski
     * (ör. Japonca kalmış) kayıtlar servis edilmez.
     */
    private fun cacheKey(name: String, page: Int, language: String): String =
        "${name}_p${page}_${language.lowercase()}_v${MediaTitleResolver.VERSION}"

    fun get(key: String): List<JikanSearchResult>? {
        val entry = cache[key] ?: return null
        return if (entry.isFresh()) entry.data else null
    }

    fun put(key: String, data: List<JikanSearchResult>) {
        cache[key] = CacheEntry(data, System.currentTimeMillis())
    }

    // ── Trending (fallback: /discover?sort_by=popularity.desc) ─────────────────

    suspend fun getTrendingMovies(
        page: Int = 1,
        apiKey: String,
        language: String,
        executeGet: suspend (String) -> String?
    ): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        val key = cacheKey("trending_movies", page, language)
        get(key)?.let { return@withContext it }

        // Önce trending endpoint'i dene
        val trendUrl = "https://api.themoviedb.org/3/trending/movie/week?api_key=$apiKey&language=$language&page=$page"
        var result = parseTmdbDiscoverList(trendUrl, MediaType.Movie, executeGet)

        // Trending boş döndüyse discover fallback kullan
        if (result.isEmpty()) {
            Log.w(TAG, "getTrendingMovies: trending empty, falling back to discover/popularity")
            val fallbackUrl = "https://api.themoviedb.org/3/discover/movie?api_key=$apiKey&language=$language&sort_by=popularity.desc&page=$page"
            result = parseTmdbDiscoverList(fallbackUrl, MediaType.Movie, executeGet)
        }

        if (result.isNotEmpty()) put(key, result)
        result
    }

    suspend fun getTrendingShows(
        page: Int = 1,
        apiKey: String,
        language: String,
        executeGet: suspend (String) -> String?
    ): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        val key = cacheKey("trending_shows", page, language)
        get(key)?.let { return@withContext it }

        val trendUrl = "https://api.themoviedb.org/3/trending/tv/week?api_key=$apiKey&language=$language&page=$page"
        var result = parseTmdbDiscoverList(trendUrl, MediaType.TvShow, executeGet)

        if (result.isEmpty()) {
            Log.w(TAG, "getTrendingShows: trending empty, falling back to discover/popularity")
            val fallbackUrl = "https://api.themoviedb.org/3/discover/tv?api_key=$apiKey&language=$language&sort_by=popularity.desc&page=$page"
            result = parseTmdbDiscoverList(fallbackUrl, MediaType.TvShow, executeGet)
        }

        if (result.isNotEmpty()) put(key, result)
        result
    }

    /**
     * TMDB'den bu hafta trend olan tüm medyayı (film + dizi) çeker.
     * Fallback: film ve dizi popular listelerini birleştirip karıştırır.
     */
    suspend fun getTrendingAll(
        page: Int = 1,
        apiKey: String,
        language: String,
        executeGet: suspend (String) -> String?
    ): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        val key = cacheKey("trending_all", page, language)
        get(key)?.let { return@withContext it }

        val trendUrl = "https://api.themoviedb.org/3/trending/all/week?api_key=$apiKey&language=$language&page=$page"
        var result = parseTmdbDiscoverListAll(trendUrl, executeGet)

        if (result.isEmpty()) {
            Log.w(TAG, "getTrendingAll: trending empty, merging movie+tv popular fallback")
            val moviesUrl = "https://api.themoviedb.org/3/discover/movie?api_key=$apiKey&language=$language&sort_by=popularity.desc&page=$page"
            val showsUrl  = "https://api.themoviedb.org/3/discover/tv?api_key=$apiKey&language=$language&sort_by=popularity.desc&page=$page"
            val movies = parseTmdbDiscoverList(moviesUrl, MediaType.Movie, executeGet)
            val shows  = parseTmdbDiscoverList(showsUrl, MediaType.TvShow, executeGet)
            // Film ve dizileri interleave et: 1 film, 1 dizi sırayla
            val merged = mutableListOf<JikanSearchResult>()
            val maxSize = maxOf(movies.size, shows.size)
            for (i in 0 until maxSize) {
                if (i < movies.size) merged.add(movies[i])
                if (i < shows.size) merged.add(shows[i])
            }
            // Birleşik sayfa: en az bir tam API sayfası döndür (sayfalama kayıpsız olsun).
            result = merged.take(TMDB_PAGE_SIZE)
        }

        if (result.isNotEmpty()) put(key, result)
        result
    }

    // ── Popular ─────────────────────────────────────────────────────────────────

    /**
     * En popüler aksiyon/macera filmlerini çeker; "Ev Sineması" şeridi için kullanılır.
     */
    suspend fun getPopularMovies(
        page: Int = 1,
        apiKey: String,
        language: String,
        executeGet: suspend (String) -> String?
    ): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        val key = cacheKey("popular_movies", page, language)
        get(key)?.let { return@withContext it }
        val url = "https://api.themoviedb.org/3/movie/popular?api_key=$apiKey&language=$language&page=$page"
        val result = parseTmdbDiscoverList(url, MediaType.Movie, executeGet)
        if (result.isNotEmpty()) put(key, result)
        result
    }

    /** En popüler dizileri çeker — Keşfet TMDB sekmesi için */
    suspend fun getPopularShows(
        page: Int = 1,
        apiKey: String,
        language: String,
        executeGet: suspend (String) -> String?
    ): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        val key = cacheKey("popular_shows", page, language)
        get(key)?.let { return@withContext it }
        val url = "https://api.themoviedb.org/3/tv/popular?api_key=$apiKey&language=$language&page=$page"
        val result = parseTmdbDiscoverList(url, MediaType.TvShow, executeGet)
        if (result.isNotEmpty()) put(key, result)
        result
    }

    // ── Top Rated ───────────────────────────────────────────────────────────────

    /** En yüksek puanlı filmleri çeker — Keşfet TMDB sekmesi için */
    suspend fun getTopRatedMovies(
        page: Int = 1,
        apiKey: String,
        language: String,
        executeGet: suspend (String) -> String?
    ): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        val key = cacheKey("top_rated_movies", page, language)
        get(key)?.let { return@withContext it }
        val url = "https://api.themoviedb.org/3/movie/top_rated?api_key=$apiKey&language=$language&page=$page"
        val result = parseTmdbDiscoverList(url, MediaType.Movie, executeGet)
        if (result.isNotEmpty()) put(key, result)
        result
    }

    /** En yüksek puanlı dizileri çeker — Keşfet TMDB sekmesi için */
    suspend fun getTopRatedShows(
        page: Int = 1,
        apiKey: String,
        language: String,
        executeGet: suspend (String) -> String?
    ): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        val key = cacheKey("top_rated_shows", page, language)
        get(key)?.let { return@withContext it }
        val url = "https://api.themoviedb.org/3/tv/top_rated?api_key=$apiKey&language=$language&page=$page"
        val result = parseTmdbDiscoverList(url, MediaType.TvShow, executeGet)
        if (result.isNotEmpty()) put(key, result)
        result
    }

    /**
     * TMDB keşfet endpoint'i üzerinden belirli bir tür ID'sine göre içerik çeker.
     */
    suspend fun discoverByGenre(
        genreId: Int,
        isMovie: Boolean,
        apiKey: String,
        language: String,
        executeGet: suspend (String) -> String?
    ): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        val key = cacheKey("discover_genre_${genreId}_${if (isMovie) "movie" else "tv"}", 1, language)
        get(key)?.let { return@withContext it }

        val endpoint = if (isMovie) "movie" else "tv"
        val mediaType = if (isMovie) MediaType.Movie else MediaType.TvShow
        val url = "https://api.themoviedb.org/3/discover/$endpoint?api_key=$apiKey&language=$language&with_genres=$genreId&sort_by=popularity.desc&page=1"
        val result = parseTmdbDiscoverList(url, mediaType, executeGet)

        if (result.isNotEmpty()) put(key, result)
        result
    }

    suspend fun getTrendingMedia(
        page: Int = 1,
        apiKey: String,
        language: String,
        executeGet: suspend (String) -> String?
    ): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        val key = cacheKey("trending_media", page, language)
        get(key)?.let { return@withContext it }

        val tvUrl = "https://api.themoviedb.org/3/discover/tv?api_key=$apiKey&language=$language&with_genres=16&with_original_language=ja&sort_by=popularity.desc&page=$page"
        val tvResult = parseTmdbDiscoverList(tvUrl, MediaType.Anime, executeGet)

        val movieUrl = "https://api.themoviedb.org/3/discover/movie?api_key=$apiKey&language=$language&with_genres=16&with_original_language=ja&sort_by=popularity.desc&page=$page"
        val movieResult = parseTmdbDiscoverList(movieUrl, MediaType.Anime, executeGet)

        val merged = mutableListOf<JikanSearchResult>()
        val maxSize = maxOf(tvResult.size, movieResult.size)
        for (i in 0 until maxSize) {
            if (i < tvResult.size) merged.add(tvResult[i])
            if (i < movieResult.size) merged.add(movieResult[i])
        }
        // Birleşik sayfa: en az bir tam API sayfası döndür (sayfalama kayıpsız olsun).
        val result = merged.take(TMDB_PAGE_SIZE)

        if (result.isNotEmpty()) put(key, result)
        result
    }

    suspend fun getPopularMedia(
        page: Int = 1,
        apiKey: String,
        language: String,
        executeGet: suspend (String) -> String?
    ): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        val key = cacheKey("popular_media", page, language)
        get(key)?.let { return@withContext it }

        val tvUrl = "https://api.themoviedb.org/3/discover/tv?api_key=$apiKey&language=$language&with_genres=16&with_original_language=ja&sort_by=vote_count.desc&page=$page"
        val tvResult = parseTmdbDiscoverList(tvUrl, MediaType.Anime, executeGet)

        val movieUrl = "https://api.themoviedb.org/3/discover/movie?api_key=$apiKey&language=$language&with_genres=16&with_original_language=ja&sort_by=vote_count.desc&page=$page"
        val movieResult = parseTmdbDiscoverList(movieUrl, MediaType.Anime, executeGet)

        val merged = mutableListOf<JikanSearchResult>()
        val maxSize = maxOf(tvResult.size, movieResult.size)
        for (i in 0 until maxSize) {
            if (i < tvResult.size) merged.add(tvResult[i])
            if (i < movieResult.size) merged.add(movieResult[i])
        }
        // Birleşik sayfa: en az bir tam API sayfası döndür (sayfalama kayıpsız olsun).
        val result = merged.take(TMDB_PAGE_SIZE)

        if (result.isNotEmpty()) put(key, result)
        result
    }

    suspend fun getTopRatedAnime(
        page: Int = 1,
        apiKey: String,
        language: String,
        executeGet: suspend (String) -> String?
    ): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        val key = cacheKey("top_rated_anime", page, language)
        get(key)?.let { return@withContext it }

        val tvUrl = "https://api.themoviedb.org/3/discover/tv?api_key=$apiKey&language=$language&with_genres=16&with_original_language=ja&sort_by=vote_average.desc&vote_count.gte=200&page=$page"
        val tvResult = parseTmdbDiscoverList(tvUrl, MediaType.Anime, executeGet)

        val movieUrl = "https://api.themoviedb.org/3/discover/movie?api_key=$apiKey&language=$language&with_genres=16&with_original_language=ja&sort_by=vote_average.desc&vote_count.gte=100&page=$page"
        val movieResult = parseTmdbDiscoverList(movieUrl, MediaType.Anime, executeGet)

        val merged = mutableListOf<JikanSearchResult>()
        val maxSize = maxOf(tvResult.size, movieResult.size)
        for (i in 0 until maxSize) {
            if (i < tvResult.size) merged.add(tvResult[i])
            if (i < movieResult.size) merged.add(movieResult[i])
        }
        // Birleşik sayfa: en az bir tam API sayfası döndür (sayfalama kayıpsız olsun).
        val result = merged.take(TMDB_PAGE_SIZE)

        if (result.isNotEmpty()) put(key, result)
        result
    }

    suspend fun getUpcomingMedia(
        page: Int = 1,
        apiKey: String,
        language: String,
        executeGet: suspend (String) -> String?
    ): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        val key = cacheKey("upcoming_media", page, language)
        get(key)?.let { return@withContext it }

        // tv/on_the_air: Bu hafta yayında olan diziler — hepsi posterlidir
        val tvUrl = "https://api.themoviedb.org/3/tv/on_the_air?api_key=$apiKey&language=$language&page=$page"
        val tvResult = parseTmdbDiscoverList(tvUrl, MediaType.TvShow, executeGet)

        // movie/upcoming: TMDB resmi yakında çıkacak filmler endpointi — hepsi posterlidir
        val movieUrl = "https://api.themoviedb.org/3/movie/upcoming?api_key=$apiKey&language=$language&page=$page"
        val movieResult = parseTmdbDiscoverList(movieUrl, MediaType.Movie, executeGet)

        val merged = mutableListOf<JikanSearchResult>()
        merged.addAll(tvResult)
        merged.addAll(movieResult)

        // Sadece poster/görseli olan öğeleri dahil et; nextAiringEpisode'a göre sırala
        val result = merged
            .filter { it.imageUrl != null }
            .sortedBy { item ->
                item.nextAiringEpisode?.split("|")?.getOrNull(1)?.toLongOrNull() ?: Long.MAX_VALUE
            }.take(TMDB_PAGE_SIZE)

        if (result.isNotEmpty()) put(key, result)
        result
    }



    // ── Backdrop by Title & ID ──────────────────────────────────────────────────

    /**
     * Başlığa göre backdrop çözer. Film/dizi sonuçları türüne göre aranır; anime
     * başlıklarında film ve TV sonuçları birlikte değerlendirilir. Başlık ve yıl
     * eşleşmesi zayıfsa yanlış bir serinin görselini göstermek yerine null döner.
     */
    suspend fun fetchBackdropByTitle(
        title: String,
        mediaType: MediaType?,
        year: Int?,
        apiKey: String,
        language: String,
        executeGet: suspend (String) -> String?
    ): String? = withContext(Dispatchers.IO) {
        val cleanTitle = title.trim().take(100)
        if (cleanTitle.isBlank() || mediaType == MediaType.Manga) return@withContext null
        val typeKey = when (mediaType) {
            MediaType.Movie -> "movie"
            MediaType.TvShow -> "tv"
            else -> "multi"
        }
        val normalizedKeyTitle = normalizeBackdropTitle(cleanTitle).replace(' ', '_')
        val cacheKey = cacheKey("backdrop_$typeKey", 1, language) + "_${year ?: 0}_$normalizedKeyTitle"
        val cached = get(cacheKey)
        if (cached != null) return@withContext cached.firstOrNull()?.backdropUrl

        try {
            val encodedTitle = java.net.URLEncoder.encode(cleanTitle, "UTF-8")
            val endpoint = if (typeKey == "multi") "multi" else typeKey
            val yearParam = when (typeKey) {
                "movie" -> year?.let { "&year=$it" }.orEmpty()
                "tv" -> year?.let { "&first_air_date_year=$it" }.orEmpty()
                else -> ""
            }
            val url = "https://api.themoviedb.org/3/search/$endpoint?api_key=$apiKey&language=$language&query=$encodedTitle&page=1$yearParam"
            val responseText = executeGet(url) ?: return@withContext null
            val results = JSONObject(responseText).optJSONArray("results") ?: return@withContext null

            data class Match(val score: Int, val backdropUrl: String)
            val matches = mutableListOf<Match>()
            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                val foundType = when (typeKey) {
                    "movie" -> "movie"
                    "tv" -> "tv"
                    else -> item.optString("media_type", "")
                }
                if (foundType != "movie" && foundType != "tv") continue

                val backdropPath = item.optNullableString("backdrop_path")?.takeIf { it.isNotBlank() } ?: continue
                val candidateNames = listOfNotNull(
                    item.optNullableString("title"),
                    item.optNullableString("name"),
                    item.optNullableString("original_title"),
                    item.optNullableString("original_name")
                )
                val titleScore = candidateNames.maxOfOrNull { backdropTitleMatchScore(cleanTitle, it) } ?: 0
                if (titleScore < 55) continue

                val date = if (foundType == "movie") {
                    item.optNullableString("release_date")
                } else {
                    item.optNullableString("first_air_date")
                }
                val resultYear = date?.take(4)?.toIntOrNull()
                val yearScore = when {
                    year == null || resultYear == null -> 0
                    year == resultYear -> 20
                    kotlin.math.abs(year - resultYear) == 1 -> 8
                    else -> -20
                }
                if (year != null && yearScore < 0 && titleScore < 90) continue
                matches += Match(titleScore + yearScore, "https://image.tmdb.org/t/p/w1280$backdropPath")
            }

            val best = matches.maxByOrNull { it.score }
            if (best == null) {
                put(cacheKey, emptyList())
                return@withContext null
            }
            put(cacheKey, listOf(JikanSearchResult(
                malId = 0, title = cleanTitle, subtitle = "", type = mediaType ?: MediaType.Anime,
                total = null, score = null, isAdult = false, imageUrl = null, year = year,
                source = "tmdb", backdropUrl = best.backdropUrl
            )))
            best.backdropUrl
        } catch (e: Exception) {
            Log.e(TAG, "fetchBackdropByTitle error: ${e.message}")
            null
        }
    }

    /** TMDB kaynaklı kayıtlar için başlık araması yerine tam ID'den yatay görsel alır. */
    suspend fun fetchBackdropByTmdbId(
        tmdbId: Int,
        isMovie: Boolean,
        apiKey: String,
        language: String,
        executeGet: suspend (String) -> String?
    ): String? = withContext(Dispatchers.IO) {
        if (tmdbId <= 0) return@withContext null
        val mediaPath = if (isMovie) "movie" else "tv"
        val key = cacheKey("backdrop_id_${mediaPath}", tmdbId, language)
        get(key)?.let { return@withContext it.firstOrNull()?.backdropUrl }

        try {
            val url = "https://api.themoviedb.org/3/$mediaPath/$tmdbId/images?api_key=$apiKey&include_image_language=en,null"
            val responseText = executeGet(url) ?: return@withContext null
            val backdrops = JSONObject(responseText).optJSONArray("backdrops")
            val candidates = mutableListOf<Triple<Double, Int, String>>()
            if (backdrops != null) {
                for (i in 0 until backdrops.length()) {
                    val item = backdrops.optJSONObject(i) ?: continue
                    val path = item.optNullableString("file_path")?.takeIf { it.isNotBlank() } ?: continue
                    val aspect = item.optDouble("aspect_ratio", 0.0)
                    if (aspect > 0.0 && aspect < 1.2) continue
                    val rating = item.optDouble("vote_average", 0.0)
                    val votes = item.optInt("vote_count", 0)
                    candidates += Triple(rating, votes, "https://image.tmdb.org/t/p/w1280$path")
                }
            }
            val selected = candidates
                .sortedWith(compareByDescending<Triple<Double, Int, String>> { it.second }.thenByDescending { it.first })
                .firstOrNull()
                ?.third
            put(key, selected?.let { image ->
                listOf(JikanSearchResult(
                    malId = tmdbId, title = "", subtitle = "",
                    type = if (isMovie) MediaType.Movie else MediaType.TvShow,
                    total = null, score = null, isAdult = false, imageUrl = null, year = null,
                    source = "tmdb", backdropUrl = image, tmdbId = tmdbId
                ))
            }.orEmpty())
            selected
        } catch (e: Exception) {
            Log.e(TAG, "fetchBackdropByTmdbId error (id=$tmdbId, movie=$isMovie): ${e.message}")
            null
        }
    }

    private fun normalizeBackdropTitle(value: String): String =
        java.text.Normalizer.normalize(value.lowercase(java.util.Locale.ROOT), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()

    internal fun backdropTitleMatchScore(query: String, candidate: String): Int {
        val queryNormalized = normalizeBackdropTitle(query)
        val candidateNormalized = normalizeBackdropTitle(candidate)
        if (queryNormalized.isBlank() || candidateNormalized.isBlank()) return 0
        val queryCompact = queryNormalized.replace(" ", "")
        val candidateCompact = candidateNormalized.replace(" ", "")
        if (queryCompact == candidateCompact) return 100
        if (candidateCompact.startsWith(queryCompact) || queryCompact.startsWith(candidateCompact)) return 88
        if (candidateCompact.contains(queryCompact) || queryCompact.contains(candidateCompact)) return 78
        val queryTokens = queryNormalized.split(' ').filter { it.length > 1 }.toSet()
        val candidateTokens = candidateNormalized.split(' ').filter { it.length > 1 }.toSet()
        if (queryTokens.isEmpty() || candidateTokens.isEmpty()) return 0
        val overlap = queryTokens.intersect(candidateTokens).size.toDouble() / maxOf(queryTokens.size, candidateTokens.size)
        return (overlap * 72).toInt()
    }

    // ── Private Parsers ─────────────────────────────────────────────────────────

    /**
     * Aynı sayfanın İngilizce (en-US) başlıklarını id → başlık haritası olarak çeker.
     *
     * ⚠️ [TmdbUrlUtils.withLanguage] kullanılır: `with_original_language` parametresi
     * bozulmadığı için İngilizce isteği aynı içerik listesini döner ve ID eşleşmesi tutar.
     *
     * Yedek istek boş/hatalı dönerse geri çekilmeli (backoff) olarak yeniden denenir:
     * harita boş kalırsa Türkçesi olmayan CJK başlıklar ekrana düşer. Keşfet açılışı
     * ~12 şeridi paralel çeker ve her biri bu isteği tetikleyebildiği için art arda
     * (beklemesiz) denemek paylaşımlı API anahtarında 429/timeout'a takılıyordu;
     * 300ms → 700ms beklemeyle 3 deneme yapılır.
     */
    private suspend fun fetchEnglishTitlesMap(
        url: String,
        executeGet: suspend (String) -> String?
    ): Map<Int, String> {
        val enUrl = TmdbUrlUtils.englishVariant(url)
        val backoffDelaysMs = longArrayOf(0L, 300L, 700L)
        for ((attempt, delayMs) in backoffDelaysMs.withIndex()) {
            if (delayMs > 0L) delay(delayMs)
            try {
                val map = TmdbTitleFallback.parseEnglishTitles(executeGet(enUrl))
                if (map.isNotEmpty()) return map
            } catch (e: Exception) {
                Log.e(TAG, "fetchEnglishTitlesMap error (deneme ${attempt + 1}): ${e.message}")
            }
        }
        Log.w(TAG, "fetchEnglishTitlesMap: en-US başlık haritası alınamadı — $enUrl")
        return emptyMap()
    }

    private suspend fun parseTmdbDiscoverList(
        url: String,
        mediaType: MediaType,
        executeGet: suspend (String) -> String?
    ): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        try {
            val responseText = executeGet(url) ?: return@withContext emptyList()
            val root = JSONObject(responseText)
            val results = root.optJSONArray("results") ?: return@withContext emptyList()

            val isMovieUrl = url.contains("/movie")
            val isMovieOf: (JSONObject) -> Boolean = { isMovieUrl || mediaType == MediaType.Movie }
            val enTitlesMap = if (TmdbTitleFallback.needsEnglishFallback(results, url, isMovieOf)) {
                fetchEnglishTitlesMap(url, executeGet)
            } else null

            val list = mutableListOf<JikanSearchResult>()
            for (i in 0 until minOf(results.length(), TMDB_PAGE_SIZE)) {
                val item = results.optJSONObject(i) ?: continue
                val tmdbId = item.optInt("id", 0).takeIf { it > 0 } ?: continue
                val isMovie = isMovieUrl || mediaType == MediaType.Movie
                val localizedTitle = if (isMovie) item.optString("title", "") else item.optString("name", "")
                if (localizedTitle.isBlank()) continue
                val posterPath = item.optNullableString("poster_path") ?: ""
                val backdropPath = item.optNullableString("backdrop_path") ?: ""
                val backdropUrl = if (backdropPath.isNotEmpty()) "https://image.tmdb.org/t/p/w1280$backdropPath" else null
                val releaseDate = if (isMovie) item.optString("release_date", "") else item.optString("first_air_date", "")
                val year = releaseDate.take(4).toIntOrNull()
                val rating = item.optDouble("vote_average", 0.0)
                val score = if (rating > 0.0) (rating * 10).toInt().coerceIn(0, 100) / 10 else null
                val imageUrl = if (posterPath.isNotEmpty()) "https://image.tmdb.org/t/p/w500$posterPath" else null
                val actualType = if (isMovie) MediaType.Movie else if (mediaType == MediaType.Anime) MediaType.Anime else MediaType.TvShow
                val subtitleParts = buildList {
                    if (mediaType == MediaType.Anime) {
                        add("Anime")
                        add(if (isMovie) "Film" else "Dizi")
                    } else {
                        add(if (isMovie) "Film" else "Dizi")
                    }
                    if (year != null && year > 0) add(year.toString())
                }
                val nextAiringEpisode = try {
                    if (releaseDate.isNotEmpty()) {
                        val epoch = com.kitsugi.animelist.utils.NextAiringFormat.isoDateToEpoch(releaseDate)
                        val nowSeconds = System.currentTimeMillis() / 1000L
                        if (epoch != null && epoch > nowSeconds) {
                            // 0 = Film (vizyon), -1 = bölüm numarası bilinmeyen dizi
                            "${if (isMovie) 0 else -1}|$epoch"
                        } else null
                    } else null
                } catch (e: Exception) {
                    null
                }
                // Türkçe → İngilizce → Romaji zinciri (CJK başlık ekrana düşmez)
                val resolved = TmdbTitleFallback.resolve(
                    item = item,
                    isMovie = isMovie,
                    url = url,
                    englishTitle = enTitlesMap?.get(tmdbId)
                )

                list.add(
                    JikanSearchResult(
                        malId = tmdbId, title = resolved.display,
                        subtitle = subtitleParts.joinToString(", "),
                        type = actualType, total = null, score = score,
                        isAdult = item.optBoolean("adult", false),
                        imageUrl = imageUrl, year = year, source = "tmdb",
                        realMalId = null, titleEnglish = resolved.english, titleJapanese = resolved.native,
                        tmdbId = tmdbId, backdropUrl = backdropUrl,
                        nextAiringEpisode = nextAiringEpisode
                    )
                )
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "parseTmdbDiscoverList error: ${e.message}", e)
            emptyList()
        }
    }

    private suspend fun parseTmdbDiscoverListAll(
        url: String,
        executeGet: suspend (String) -> String?
    ): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        try {
            val responseText = executeGet(url) ?: return@withContext emptyList()
            val root = JSONObject(responseText)
            val results = root.optJSONArray("results") ?: return@withContext emptyList()

            val isMovieOf: (JSONObject) -> Boolean = { it.optString("media_type", "movie") == "movie" }
            val enTitlesMap = if (TmdbTitleFallback.needsEnglishFallback(results, url, isMovieOf)) {
                fetchEnglishTitlesMap(url, executeGet)
            } else null

            val list = mutableListOf<JikanSearchResult>()
            for (i in 0 until minOf(results.length(), TMDB_PAGE_SIZE)) {
                val item = results.optJSONObject(i) ?: continue
                val tmdbId = item.optInt("id", 0).takeIf { it > 0 } ?: continue
                val mediaTypeStr = item.optString("media_type", "movie")
                if (mediaTypeStr == "person") continue
                val isMovie = mediaTypeStr == "movie"
                val mediaType = if (isMovie) MediaType.Movie else MediaType.TvShow
                val localizedTitle = if (isMovie) item.optString("title", "") else item.optString("name", "")
                if (localizedTitle.isBlank()) continue
                val posterPath = item.optNullableString("poster_path") ?: ""
                val backdropPath = item.optNullableString("backdrop_path") ?: ""
                val backdropUrl = if (backdropPath.isNotEmpty()) "https://image.tmdb.org/t/p/w1280$backdropPath" else null
                val releaseDate = if (isMovie) item.optString("release_date", "") else item.optString("first_air_date", "")
                val year = releaseDate.take(4).toIntOrNull()
                val rating = item.optDouble("vote_average", 0.0)
                val score = if (rating > 0.0) (rating * 10).toInt().coerceIn(0, 100) / 10 else null
                val imageUrl = if (posterPath.isNotEmpty()) "https://image.tmdb.org/t/p/w500$posterPath" else null
                val subtitleParts = buildList {
                    add(if (isMovie) "Film" else "Dizi")
                    if (year != null && year > 0) add(year.toString())
                }
                val nextAiringEpisode = try {
                    if (releaseDate.isNotEmpty()) {
                        val epoch = com.kitsugi.animelist.utils.NextAiringFormat.isoDateToEpoch(releaseDate)
                        val nowSeconds = System.currentTimeMillis() / 1000L
                        if (epoch != null && epoch > nowSeconds) {
                            // 0 = Film (vizyon), -1 = bölüm numarası bilinmeyen dizi
                            "${if (isMovie) 0 else -1}|$epoch"
                        } else null
                    } else null
                } catch (e: Exception) {
                    null
                }
                // Türkçe → İngilizce → Romaji zinciri (CJK başlık ekrana düşmez)
                val resolved = TmdbTitleFallback.resolve(
                    item = item,
                    isMovie = isMovie,
                    url = url,
                    englishTitle = enTitlesMap?.get(tmdbId)
                )

                list.add(
                    JikanSearchResult(
                        malId = tmdbId, title = resolved.display,
                        subtitle = subtitleParts.joinToString(", "),
                        type = mediaType, total = null, score = score,
                        isAdult = item.optBoolean("adult", false),
                        imageUrl = imageUrl, year = year, source = "tmdb",
                        realMalId = null, titleEnglish = resolved.english, titleJapanese = resolved.native,
                        tmdbId = tmdbId, backdropUrl = backdropUrl,
                        nextAiringEpisode = nextAiringEpisode
                    )
                )
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "parseTmdbDiscoverListAll error: ${e.message}", e)
            emptyList()
        }
    }
}
