package com.kitsugi.animelist.data.remote

import android.util.Log
import com.kitsugi.animelist.KitsugiApplication
import com.kitsugi.animelist.R
import com.kitsugi.animelist.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import com.kitsugi.animelist.utils.*
import java.util.concurrent.TimeUnit

/**
 * TMDB API ana giriş noktası (Orkestrasyon katmanı).
 *
 * İç iş mantığı aşağıdaki modüllere delege edilir:
 *  - [TmdbMediaDetailClient]  → medya detayı, görseller, izleme sağlayıcıları, videolar
 *  - [TmdbCreditsClient]      → oyuncu/ekip, yorumlar, öneriler, istatistikler
 *  - [TmdbDiscoverClient]     → trending/popular/top-rated keşfet + backdrop arama
 *
 * Kullanıcı API anahtarı boşsa dahili yedek anahtar devreye girer.
 */
class TmdbApiClient(
    /** Kullanıcı API anahtarı — boşsa dahili anahtar [BUILT_IN_API_KEY] devreye girer */
    private val userApiKey: String = "",
    private val userLanguage: String = ""
) {
    /**
     * TMDB'ye özgü OkHttpClient — kısa timeout ile.
     * Global KitsugiHttpClient 15s kullanırken TMDB trending endpoint'leri
     * timeout'a girebilir. 8s ile hızlı fail + /discover fallback devreye girer.
     */
    private val client = OkHttpClient.Builder()
        .dns(com.kitsugi.animelist.core.network.IPv4FirstDns())
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor(com.kitsugi.animelist.core.network.PlatformFailureInterceptor())
        // v2.4.342: TMDB çağrıları da kalıcı günlüğe düşsün.
        .addInterceptor(com.kitsugi.animelist.core.network.KitsugiNetworkLogInterceptor())
        .build()
    // Most call sites use TmdbApiClient() and are not rebuilt when preferences change.
    // Resolve the shared active key here so personal keys work consistently throughout the app.
    private val apiKey: String
        get() = userApiKey.trim().ifBlank { getActiveApiKey() }
    private val language: String
        get() = userLanguage.trim().ifBlank { getActiveLanguage() }
    private val TAG = "TmdbApiClient"

    companion object {
        /**
         * Dahili (yedek) TMDB API anahtarı.
         * Öncelik sırası:
         *  1. local.properties'teki `tmdb_api_key=` (BuildConfig üzerinden)
         *  2. Uygulama ayarlarındaki kullanıcı anahtarı (TmdbApiClient'e userApiKey olarak geçilir)
         *  3. Bu fallback (açık kaynak — rate-limit paylaşımlı)
         */
        private const val FALLBACK_KEY = "8265bd1679663a7ea12ac168da84d2e8"

        // Bellek içi önbellek — OkHttp thread pool'unu bloke etmemek için
        // runBlocking kullanmak yerine son bilinen değer tutulur.
        @Volatile private var cachedApiKey: String? = null
        @Volatile private var cachedLanguage: String? = null

        val BUILT_IN_API_KEY: String get() {
            val fromBuild = com.kitsugi.animelist.BuildConfig.TMDB_API_KEY
            return if (fromBuild.isBlank() || fromBuild == "YOUR_TMDB_API_KEY_HERE") {
                FALLBACK_KEY
            } else {
                fromBuild
            }
        }

        fun resolveApiKey(userKey: String): String = userKey.trim().ifBlank { BUILT_IN_API_KEY }

        /**
         * API anahtarını hafif bir yetkilendirilmiş istekle doğrular.
         * Boş anahtar, uygulamadaki ortak/built-in anahtarı test eder.
         */
        suspend fun validateApiKey(userKey: String): Boolean = withContext(Dispatchers.IO) {
            val keyToCheck = resolveApiKey(userKey)
            val url = okhttp3.HttpUrl.Builder()
                .scheme("https")
                .host("api.themoviedb.org")
                .addPathSegments("3/configuration")
                .addQueryParameter("api_key", keyToCheck)
                .build()
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "KitsugiAnimeList/1.0")
                .build()
            val validationClient = com.kitsugi.animelist.core.network.KitsugiHttpClient.metadataClient
                .newBuilder()
                .callTimeout(10, TimeUnit.SECONDS)
                .build()

            try {
                validationClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w("TmdbApiClient", "TMDB key validation failed: HTTP ${response.code}")
                        return@withContext false
                    }
                    val body = response.body?.string() ?: return@withContext false
                    JSONObject(body).optJSONObject("images") != null
                }
            } catch (e: Exception) {
                Log.w("TmdbApiClient", "TMDB key validation failed (${e.javaClass.simpleName})")
                false
            }
        }

        /**
         * Cache'lenmiş API key'i döner. runBlocking KULLANILMAZ.
         * İlk çağrıda varsayılan döner; ViewModel/Repository katmanı
         * [updateCache] ile arka planda güncel değeri enjekte eder.
         */
        fun getActiveApiKey(): String = cachedApiKey ?: BUILT_IN_API_KEY

        /**
         * Cache'lenmiş dil ayarını döner. runBlocking KULLANILMAZ.
         */
        fun getActiveLanguage(): String = cachedLanguage ?: "tr-TR"

        /**
         * ViewModel veya Repository katmanından, coroutine içinden çağrılır.
         * OkHttp thread'ini bloke etmez.
         */
        fun updateCache(apiKey: String, language: String) {
            cachedApiKey = resolveApiKey(apiKey)
            cachedLanguage = when (language.lowercase()) {
                "tr" -> "tr-TR"
                "en" -> "en-US"
                "ja" -> "ja-JP"
                "fr" -> "fr-FR"
                "de" -> "de-DE"
                "es" -> "es-ES"
                "it" -> "it-IT"
                "ru" -> "ru-RU"
                "zh" -> "zh-CN"
                else -> language.ifBlank { "tr-TR" }
            }
        }
    }

    private suspend fun currentSettings(): com.kitsugi.animelist.data.settings.AppSettings? {
        val context = com.kitsugi.animelist.KitsugiApplication.getInstance()?.applicationContext ?: return null
        return try {
            withTimeoutOrNull(800L) {
                com.kitsugi.animelist.data.settings.SettingsDataStore(context).settingsFlow.first()
            }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun isTmdbEnabled(): Boolean = currentSettings()?.tmdbEnabled ?: true

    private suspend fun isTmdbFeatureEnabled(feature: (com.kitsugi.animelist.data.settings.AppSettings) -> Boolean): Boolean {
        val settings = currentSettings() ?: return true
        return settings.tmdbEnabled && feature(settings)
    }

    // ── Medya Detayı ────────────────────────────────────────────────────────────

    suspend fun fetchMediaDetail(tmdbId: Int, isMovie: Boolean): KitsugiMediaDetail? {
        val settings = currentSettings()
        if (settings?.tmdbEnabled == false) return null
        val features = TmdbDetailFeatures(
            useBasicInfo = settings?.tmdbUseBasicInfo ?: true,
            useDetails = settings?.tmdbUseDetails ?: true,
            useReleaseDates = settings?.tmdbUseReleaseDates ?: true,
            useArtwork = settings?.tmdbUseArtwork ?: true,
            useTrailers = settings?.tmdbUseTrailers ?: true,
            useProductions = settings?.tmdbUseProductions ?: true,
            useNetworks = settings?.tmdbUseNetworks ?: true
        )
        return TmdbMediaDetailClient.fetchMediaDetail(tmdbId, isMovie, apiKey, language, features, ::executeGet)
    }

    suspend fun fetchMediaImages(tmdbId: Int, isMovie: Boolean): List<String> = withContext(Dispatchers.IO) {
        if (!isTmdbFeatureEnabled { it.tmdbUseArtwork }) return@withContext emptyList()
        TmdbMediaDetailClient.fetchMediaImages(tmdbId, isMovie, apiKey, ::executeGet)
    }

    suspend fun fetchWatchProviders(tmdbId: Int, isMovie: Boolean): List<KitsugiExternalLink> = withContext(Dispatchers.IO) {
        if (!isTmdbFeatureEnabled { it.tmdbUseNetworks }) return@withContext emptyList()
        TmdbMediaDetailClient.fetchWatchProviders(tmdbId, isMovie, apiKey, ::executeGet)
    }

    suspend fun fetchVideos(tmdbId: Int, isMovie: Boolean): Pair<String?, List<KitsugiTheme>> = withContext(Dispatchers.IO) {
        if (!isTmdbFeatureEnabled { it.tmdbUseTrailers }) return@withContext Pair(null, emptyList())
        TmdbMediaDetailClient.fetchVideos(tmdbId, isMovie, apiKey, language, ::executeGet)
    }

    private val tmdbVideoItemsCache = com.kitsugi.animelist.core.memory.BoundedCache<String, List<MediaVideoItem>>("tmdb.videoItems", 120)

    /**
     * Ortak videolar sekmesi için TMDB videolarını (Fragman/Teaser, Açılış Jeneriği, Klip/Ekstra)
     * çeker. [expectedTitles] verildiyse çapraz çözülen TMDB kimliğinin gerçekten aynı esere
     * ait olduğunu doğrular; film/dizi kimlik çakışmalarında yanlış yapımın videosunu katmaz.
     */
    suspend fun fetchMediaVideoItems(
        tmdbId: Int,
        isMovie: Boolean,
        expectedTitles: List<String> = emptyList(),
        seasonNumber: Int? = null,
        ignoreFeatureGate: Boolean = false
    ): List<MediaVideoItem> = withContext(Dispatchers.IO) {
        if (tmdbId <= 0 || apiKey.isBlank()) return@withContext emptyList()
        if (!ignoreFeatureGate && !isTmdbFeatureEnabled { it.tmdbUseTrailers }) return@withContext emptyList()

        var effectiveIsMovie = isMovie
        if (expectedTitles.isNotEmpty()) {
            suspend fun verifyType(movieFlag: Boolean): Boolean {
                val typePath = if (movieFlag) "movie" else "tv"
                val metaUrl = "https://api.themoviedb.org/3/$typePath/$tmdbId?api_key=$apiKey&language=en-US&append_to_response=alternative_titles"
                val body = executeGet(metaUrl) ?: return false
                val json = runCatching { org.json.JSONObject(body) }.getOrNull() ?: return false
                val title = if (movieFlag) json.optString("title") else json.optString("name")
                val orig = if (movieFlag) json.optString("original_title") else json.optString("original_name")
                val alts = TmdbArtworkIdentity.extractAlternativeTitles(json)
                return TmdbArtworkIdentity.matches(expectedTitles, title, orig, alts)
            }

            if (!verifyType(isMovie)) {
                if (verifyType(!isMovie)) {
                    effectiveIsMovie = !isMovie
                } else {
                    return@withContext emptyList()
                }
            }
        }

        val effSeason = if (effectiveIsMovie) null else seasonNumber
        val cacheKey = "${if (effectiveIsMovie) "movie" else "tv"}:$tmdbId:${language.lowercase(java.util.Locale.ROOT)}:${effSeason ?: 0}"
        tmdbVideoItemsCache[cacheKey]?.let { return@withContext it }

        val items = TmdbMediaDetailClient.fetchStructuredVideos(
            tmdbId = tmdbId,
            isMovie = effectiveIsMovie,
            apiKey = apiKey,
            language = language,
            seasonNumber = effSeason,
            executeGet = ::executeGet
        )
        if (items.isNotEmpty()) {
            tmdbVideoItemsCache[cacheKey] = items
        }
        items
    }

    // ── Oyuncu / Ekip / İçerik ─────────────────────────────────────────────────

    suspend fun fetchCredits(tmdbId: Int, isMovie: Boolean): Pair<List<KitsugiCharacter>, List<KitsugiStaff>> = withContext(Dispatchers.IO) {
        if (!isTmdbFeatureEnabled { it.tmdbUseCredits }) return@withContext Pair(emptyList(), emptyList())
        TmdbCreditsClient.fetchCredits(tmdbId, isMovie, apiKey, language, ::executeGet)
    }

    suspend fun fetchRelations(tmdbId: Int, isMovie: Boolean): List<KitsugiRelation> = withContext(Dispatchers.IO) {
        if (!isTmdbFeatureEnabled { it.tmdbUseCollections }) return@withContext emptyList()
        TmdbCreditsClient.fetchRelations(tmdbId, isMovie, apiKey, language, ::executeGet)
    }

    suspend fun fetchRecommendations(tmdbId: Int, isMovie: Boolean): List<KitsugiRelation> = withContext(Dispatchers.IO) {
        if (!isTmdbFeatureEnabled { it.tmdbUseMoreLikeThis }) return@withContext emptyList()
        TmdbCreditsClient.fetchRecommendations(tmdbId, isMovie, apiKey, language, ::executeGet)
    }

    suspend fun fetchReviews(tmdbId: Int, isMovie: Boolean, page: Int = 1): List<KitsugiReview> = withContext(Dispatchers.IO) {
        if (!isTmdbEnabled()) return@withContext emptyList()
        TmdbCreditsClient.fetchReviews(tmdbId, isMovie, apiKey, language, ::executeGet, page)
    }

    suspend fun fetchPersonCharacterDetail(personId: Int): KitsugiCharacterDetail? = withContext(Dispatchers.IO) {
        if (!isTmdbFeatureEnabled { it.tmdbUseCredits }) return@withContext null
        TmdbCreditsClient.fetchPersonCharacterDetail(personId, apiKey, language, ::executeGet)
    }

    suspend fun fetchPersonStaffDetail(personId: Int): KitsugiStaffDetail? = withContext(Dispatchers.IO) {
        if (!isTmdbFeatureEnabled { it.tmdbUseCredits }) return@withContext null
        TmdbCreditsClient.fetchPersonStaffDetail(personId, apiKey, language, ::executeGet)
    }

    suspend fun fetchStats(tmdbId: Int, isMovie: Boolean): KitsugiStats? = withContext(Dispatchers.IO) {
        if (!isTmdbEnabled()) return@withContext null
        TmdbCreditsClient.fetchStats(tmdbId, isMovie, apiKey, language, ::executeGet)
    }

    // ── Keşfet (Trending / Popular / Top Rated) ─────────────────────────────────

    suspend fun getTrendingMovies(page: Int = 1): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        // isTmdbEnabled() burada kontrol edilmiyor — keşfet/discovery akışı
        // zaten ViewModel seviyesinde guard'landı; client başına DataStore okuma
        // race condition'a ve yavaşlamaya yol açıyordu.
        TmdbDiscoverClient.getTrendingMovies(page, apiKey, language, ::executeGet)
    }

    suspend fun getTrendingShows(page: Int = 1): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        TmdbDiscoverClient.getTrendingShows(page, apiKey, language, ::executeGet)
    }

    suspend fun getTrendingAll(page: Int = 1): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        TmdbDiscoverClient.getTrendingAll(page, apiKey, language, ::executeGet)
    }

    suspend fun getPopularMovies(page: Int = 1): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        TmdbDiscoverClient.getPopularMovies(page, apiKey, language, ::executeGet)
    }

    suspend fun getPopularShows(page: Int = 1): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        TmdbDiscoverClient.getPopularShows(page, apiKey, language, ::executeGet)
    }

    suspend fun getTopRatedMovies(page: Int = 1): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        TmdbDiscoverClient.getTopRatedMovies(page, apiKey, language, ::executeGet)
    }

    suspend fun getTopRatedShows(page: Int = 1): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        TmdbDiscoverClient.getTopRatedShows(page, apiKey, language, ::executeGet)
    }

    suspend fun getTrendingMedia(page: Int = 1): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        TmdbDiscoverClient.getTrendingMedia(page, apiKey, language, ::executeGet)
    }

    suspend fun getPopularMedia(page: Int = 1): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        TmdbDiscoverClient.getPopularMedia(page, apiKey, language, ::executeGet)
    }

    suspend fun getTopRatedAnime(page: Int = 1): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        TmdbDiscoverClient.getTopRatedAnime(page, apiKey, language, ::executeGet)
    }

    suspend fun getUpcomingMedia(page: Int = 1): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        TmdbDiscoverClient.getUpcomingMedia(page, apiKey, language, ::executeGet)
    }

    suspend fun discoverByGenre(genreId: Int, isMovie: Boolean): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        if (!isTmdbEnabled()) return@withContext emptyList()
        TmdbDiscoverClient.discoverByGenre(genreId, isMovie, apiKey, language, ::executeGet)
    }

    suspend fun fetchBackdropByTitle(
        title: String,
        mediaType: MediaType? = null,
        year: Int? = null
    ): String? = withContext(Dispatchers.IO) {
        if (!isTmdbEnabled()) return@withContext null
        TmdbDiscoverClient.fetchBackdropByTitle(title, mediaType, year, apiKey, language, ::executeGet)
    }

    /** TMDB ID'si zaten biliniyorsa yanlış başlık eşleşmesi olmadan backdrop getirir. */
    suspend fun fetchBackdropByTmdbId(tmdbId: Int, isMovie: Boolean): String? = withContext(Dispatchers.IO) {
        if (!isTmdbEnabled()) return@withContext null
        TmdbDiscoverClient.fetchBackdropByTmdbId(tmdbId, isMovie, apiKey, language, ::executeGet)
    }

    // ── Arama ───────────────────────────────────────────────────────────────────

    suspend fun search(query: String, page: Int = 1): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        if (!isTmdbEnabled()) return@withContext emptyList()
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val isTurkish = language.startsWith("tr", ignoreCase = true)
        val trUrl = "https://api.themoviedb.org/3/search/multi?api_key=$apiKey&language=$language&query=$encodedQuery&page=$page"
        val enUrl = if (isTurkish) {
            "https://api.themoviedb.org/3/search/multi?api_key=$apiKey&language=en-US&query=$encodedQuery&page=$page"
        } else null

        try {
            val deferredTr = async { executeGet(trUrl) }
            val deferredEn = if (enUrl != null) async { executeGet(enUrl) } else null

            val responseText = deferredTr.await() ?: return@withContext emptyList()
            val enResponseText = deferredEn?.await()

            // en-US sonuçlarından id -> title / name haritası çıkar
            val enTitlesMap = mutableMapOf<Int, String>()
            if (!enResponseText.isNullOrBlank()) {
                runCatching {
                    val enRoot = JSONObject(enResponseText)
                    val enResults = enRoot.optJSONArray("results")
                    if (enResults != null) {
                        for (j in 0 until enResults.length()) {
                            val enItem = enResults.getJSONObject(j)
                            val enId = enItem.optInt("id", 0)
                            val enName = if (enItem.optString("media_type") == "tv") {
                                enItem.optString("name", "")
                            } else {
                                enItem.optString("title", "")
                            }
                            val latinName = MediaTitleResolver.latin(enName)
                            if (enId > 0 && latinName != null) {
                                enTitlesMap[enId] = latinName
                            }
                        }
                    }
                }
            }

            val root = JSONObject(responseText)
            val results = root.optJSONArray("results") ?: return@withContext emptyList()
            val list = mutableListOf<JikanSearchResult>()
            for (i in 0 until minOf(results.length(), 20)) {
                val item = results.getJSONObject(i)
                val tmdbId = item.optInt("id", 0).takeIf { it > 0 } ?: continue
                val mediaTypeStr = item.optString("media_type", "movie")
                if (mediaTypeStr == "person") continue
                val isMovie = mediaTypeStr == "movie"
                val mediaType = if (isMovie) MediaType.Movie else MediaType.TvShow
                val rawTitle = if (isMovie) item.optString("title", "") else item.optString("name", "")
                if (rawTitle.isBlank()) continue

                val originalTitle = if (isMovie) item.optString("original_title", "") else item.optString("original_name", "")
                val originalLang = item.optString("original_language", "")
                val enTitle = enTitlesMap[tmdbId]?.takeIf { it.isNotBlank() }
                    ?: if (originalLang.equals("en", ignoreCase = true)) originalTitle.takeIf { it.isNotBlank() } else null

                // Türkçe (veya seçili dil) başlık yoksa veya TMDB orijinal başlığa düşmüşse
                // zincir otomatik olarak İngilizce'ye, ardından Romaji/Latin orijinale iner.
                val localizedFallback = MediaTitleResolver.isLocalizedFallback(
                    localized = rawTitle,
                    original = originalTitle,
                    requestedLanguage = language,
                    originalLanguage = originalLang
                )
                val titleInput = if (localizedFallback) null else rawTitle
                val finalTitle = MediaTitleResolver.resolve(
                    localized = titleInput,
                    english = enTitle,
                    romaji = MediaTitleResolver.latin(originalTitle),
                    original = originalTitle.ifBlank { rawTitle }
                )
                val resolvedTitleEnglish = MediaTitleResolver.resolveEnglish(
                    localized = titleInput,
                    english = enTitle,
                    romaji = MediaTitleResolver.latin(originalTitle),
                    original = originalTitle
                )
                val resolvedTitleJapanese = originalTitle
                    .ifBlank { rawTitle }
                    .takeIf {
                        originalLang in listOf("ja", "zh", "ko") ||
                            MediaTitleResolver.hasCjk(originalTitle) ||
                            MediaTitleResolver.hasCjk(rawTitle)
                    }

                val posterPath = item.optNullableString("poster_path") ?: ""
                val releaseDate = if (isMovie) item.optString("release_date", "") else item.optString("first_air_date", "")
                val year = releaseDate.take(4).toIntOrNull()
                val rating = item.optDouble("vote_average", 0.0)
                val score = if (rating > 0.0) (rating * 10).toInt().coerceIn(0, 100) / 10 else null
                val imageUrl = if (posterPath.isNotEmpty()) "https://image.tmdb.org/t/p/w500$posterPath" else null
                val genreIds = item.optJSONArray("genre_ids")?.let { genres ->
                    (0 until genres.length()).mapNotNull { genres.optInt(it, 0).takeIf { id -> id > 0 } }
                }.orEmpty()
                val subtitleParts = buildList {
                    add(if (isMovie) "movie".toLocalizedMediaTypeString() else "tv".toLocalizedMediaTypeString())
                    if (year != null && year > 0) add(year.toString())
                }
                list.add(
                    JikanSearchResult(
                        malId = tmdbId,
                        title = finalTitle,
                        subtitle = subtitleParts.joinToString(", "),
                        type = mediaType,
                        total = null,
                        score = score,
                        isAdult = item.optBoolean("adult", false),
                        imageUrl = imageUrl,
                        year = year,
                        source = "tmdb",
                        realMalId = null,
                        titleEnglish = resolvedTitleEnglish,
                        titleJapanese = resolvedTitleJapanese,
                        tmdbId = tmdbId,
                        genreIds = genreIds
                    )
                )
            }
            list
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "search error: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Text + discover facets. TMDB has no endpoint that accepts a free-text title and
     * `with_networks`/provider facets together. Search the bounded discover window and
     * apply the title terms locally instead of silently dropping the selected institution
     * or studio filter.
     */
    suspend fun searchAdvanced(
        query: String,
        isMovie: Boolean,
        page: Int = 1,
        sortBy: String = "popularity.desc",
        genres: List<Int>? = null,
        excludedGenres: List<Int>? = null,
        originCountry: String? = null,
        startYear: Int? = null,
        endYear: Int? = null,
        minScore: Double? = null,
        maxScore: Double? = null,
        minVoteCount: Int? = null,
        minRuntime: Int? = null,
        maxRuntime: Int? = null,
        watchProviderId: Int? = null,
        networkId: Int? = null,
        keyword: String? = null,
        tvStatus: String? = null,
        tvType: String? = null,
        includeAdult: Boolean = false
    ): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        val terms = query.trim().lowercase(java.util.Locale.ROOT)
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
        if (terms.isEmpty()) return@withContext emptyList()
        val pagesToInspect = (page.coerceAtLeast(1) * 3).coerceAtMost(10)
        val candidates = buildList {
            for (candidatePage in 1..pagesToInspect) {
                addAll(
                    discoverAdvanced(
                        isMovie = isMovie,
                        page = candidatePage,
                        sortBy = sortBy,
                        genres = genres,
                        excludedGenres = excludedGenres,
                        originCountry = originCountry,
                        startYear = startYear,
                        endYear = endYear,
                        minScore = minScore,
                        maxScore = maxScore,
                        minVoteCount = minVoteCount,
                        minRuntime = minRuntime,
                        maxRuntime = maxRuntime,
                        watchProviderId = watchProviderId,
                        networkId = networkId,
                        keyword = keyword,
                        tvStatus = tvStatus,
                        tvType = tvType,
                        includeAdult = includeAdult
                    )
                )
            }
        }
        candidates
            .distinctBy { "${it.type}:${it.tmdbId ?: it.malId}" }
            .filter { item ->
                val haystack = listOf(item.title, item.titleEnglish, item.titleJapanese)
                    .filterNotNull()
                    .joinToString(" ")
                    .lowercase(java.util.Locale.ROOT)
                terms.all { it in haystack }
            }
            .drop((page - 1).coerceAtLeast(0) * 20)
            .take(20)
    }

    suspend fun discoverAdvanced(
        isMovie: Boolean,
        page: Int = 1,
        sortBy: String = "popularity.desc",
        genres: List<Int>? = null,
        excludedGenres: List<Int>? = null,
        originCountry: String? = null,
        startYear: Int? = null,
        endYear: Int? = null,
        minScore: Double? = null,
        maxScore: Double? = null,
        minVoteCount: Int? = null,
        minRuntime: Int? = null,
        maxRuntime: Int? = null,
        watchProviderId: Int? = null,
        networkId: Int? = null,
        keyword: String? = null,
        tvStatus: String? = null,
        tvType: String? = null,
        includeAdult: Boolean = false
    ): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        if (!isTmdbEnabled()) return@withContext emptyList()
        val endpoint = if (isMovie) "movie" else "tv"
        val params = mutableListOf<String>()
        params.add("api_key=$apiKey")
        params.add("language=$language")
        params.add("page=$page")
        params.add("sort_by=$sortBy")
        params.add("include_adult=$includeAdult")

        if (!genres.isNullOrEmpty()) params.add("with_genres=${genres.joinToString(",")}")
        if (!excludedGenres.isNullOrEmpty()) params.add("without_genres=${excludedGenres.joinToString(",")}")
        if (!originCountry.isNullOrBlank()) params.add("with_origin_country=$originCountry")

        if (isMovie) {
            if (startYear != null) params.add("primary_release_date.gte=$startYear-01-01")
            if (endYear != null) params.add("primary_release_date.lte=$endYear-12-31")
        } else {
            if (startYear != null) params.add("first_air_date.gte=$startYear-01-01")
            if (endYear != null) params.add("first_air_date.lte=$endYear-12-31")
            if (!tvStatus.isNullOrBlank()) params.add("with_status=$tvStatus")
            if (!tvType.isNullOrBlank()) params.add("with_type=$tvType")
            if (networkId != null) params.add("with_networks=$networkId")
        }

        if (minScore != null && minScore > 0.0) params.add("vote_average.gte=$minScore")
        if (maxScore != null && maxScore > 0.0) params.add("vote_average.lte=$maxScore")
        if (minVoteCount != null && minVoteCount > 0) params.add("vote_count.gte=$minVoteCount")
        if (minRuntime != null && minRuntime > 0) params.add("with_runtime.gte=$minRuntime")
        if (maxRuntime != null && maxRuntime > 0) params.add("with_runtime.lte=$maxRuntime")

        if (watchProviderId != null && watchProviderId > 0) {
            params.add("with_watch_providers=$watchProviderId")
            params.add("watch_region=TR")
        }
        if (!keyword.isNullOrBlank()) params.add("with_keywords=$keyword")

        val url = "https://api.themoviedb.org/3/discover/$endpoint?${params.joinToString("&")}"
        try {
            val responseText = executeGet(url) ?: return@withContext emptyList()
            val root = JSONObject(responseText)
            val results = root.optJSONArray("results") ?: return@withContext emptyList()
            // Filtreli listelerde de ("Tümünü Gör" + filtre çipleri) Türkçe başlık yoksa
            // İngilizce'ye düşmek için aynı sayfanın en-US başlık haritası kullanılır.
            val enTitles = if (TmdbTitleFallback.needsEnglishFallback(results, url, { isMovie })) {
                TmdbTitleFallback.parseEnglishTitles(executeGet(TmdbUrlUtils.englishVariant(url)))
            } else emptyMap()
            val list = mutableListOf<JikanSearchResult>()
            for (i in 0 until minOf(results.length(), 24)) {
                val item = results.getJSONObject(i)
                val tmdbId = item.optInt("id", 0).takeIf { it > 0 } ?: continue
                val mediaType = if (isMovie) MediaType.Movie else MediaType.TvShow
                val title = if (isMovie) item.optString("title", "") else item.optString("name", "")
                if (title.isBlank()) continue
                val resolved = TmdbTitleFallback.resolve(
                    item = item,
                    isMovie = isMovie,
                    url = url,
                    englishTitle = enTitles[tmdbId]
                )
                val posterPath = item.optNullableString("poster_path") ?: ""
                val releaseDate = if (isMovie) item.optString("release_date", "") else item.optString("first_air_date", "")
                val year = releaseDate.take(4).toIntOrNull()
                val rating = item.optDouble("vote_average", 0.0)
                val score = if (rating > 0.0) (rating * 10).toInt().coerceIn(0, 100) / 10 else null
                val imageUrl = if (posterPath.isNotEmpty()) "https://image.tmdb.org/t/p/w500$posterPath" else null
                val genreIds = item.optJSONArray("genre_ids")?.let { genres ->
                    (0 until genres.length()).mapNotNull { genres.optInt(it, 0).takeIf { id -> id > 0 } }
                }.orEmpty()
                val subtitleParts = buildList {
                    add(if (isMovie) "movie".toLocalizedMediaTypeString() else "tv".toLocalizedMediaTypeString())
                    if (year != null && year > 0) add(year.toString())
                }
                list.add(
                    JikanSearchResult(
                        malId = tmdbId, title = resolved.display,
                        subtitle = subtitleParts.joinToString(", "),
                        type = mediaType, total = null, score = score,
                        isAdult = item.optBoolean("adult", false),
                        imageUrl = imageUrl, year = year, source = "tmdb",
                        realMalId = null, titleEnglish = resolved.english, titleJapanese = resolved.native,
                        tmdbId = tmdbId,
                        genreIds = genreIds
                    )
                )
            }
            list
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "discoverAdvanced error: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun searchPerson(query: String, page: Int = 1): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        if (!isTmdbEnabled()) return@withContext emptyList()
        val url = if (query.isNotBlank()) {
            val encodedQuery = java.net.URLEncoder.encode(query.trim(), "UTF-8")
            "https://api.themoviedb.org/3/search/person?api_key=$apiKey&language=$language&query=$encodedQuery&page=$page"
        } else {
            "https://api.themoviedb.org/3/person/popular?api_key=$apiKey&language=$language&page=$page"
        }
        try {
            val responseText = executeGet(url) ?: return@withContext emptyList()
            val root = JSONObject(responseText)
            val results = root.optJSONArray("results") ?: return@withContext emptyList()
            val list = mutableListOf<JikanSearchResult>()
            for (i in 0 until minOf(results.length(), 24)) {
                val item = results.getJSONObject(i)
                val id = item.optInt("id", 0).takeIf { it > 0 } ?: continue
                val name = item.optString("name", "")
                if (name.isBlank()) continue
                val profilePath = item.optNullableString("profile_path")
                val imageUrl = profilePath?.let { "https://image.tmdb.org/t/p/w500$it" }
                val dept = item.optString("known_for_department", "Oyuncu / Ekip")
                val pop = item.optDouble("popularity", 0.0)
                list.add(
                    JikanSearchResult(
                        malId = id, title = name,
                        subtitle = dept,
                        type = MediaType.TvShow, total = null, score = null,
                        isAdult = item.optBoolean("adult", false),
                        imageUrl = imageUrl, year = null, source = "tmdb",
                        favorites = pop.toInt()
                    )
                )
            }
            list
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun searchCompany(query: String, page: Int = 1): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        if (!isTmdbEnabled() || query.isBlank()) return@withContext emptyList()
        val encodedQuery = java.net.URLEncoder.encode(query.trim(), "UTF-8")
        val url = "https://api.themoviedb.org/3/search/company?api_key=$apiKey&query=$encodedQuery&page=$page"
        try {
            val responseText = executeGet(url) ?: return@withContext emptyList()
            val root = JSONObject(responseText)
            val results = root.optJSONArray("results") ?: return@withContext emptyList()
            val list = mutableListOf<JikanSearchResult>()
            for (i in 0 until minOf(results.length(), 24)) {
                val item = results.getJSONObject(i)
                val id = item.optInt("id", 0).takeIf { it > 0 } ?: continue
                val name = item.optString("name", "")
                if (name.isBlank()) continue
                val logoPath = item.optNullableString("logo_path")
                val imageUrl = logoPath?.let { "https://image.tmdb.org/t/p/w500$it" }
                val originCountry = item.optString("origin_country", "")
                val appContext = KitsugiApplication.getInstance()
                val companySubtitle = if (originCountry.isNotBlank()) {
                    appContext?.getString(R.string.source_production_company_country, originCountry)
                        ?: "Production company ($originCountry)"
                } else {
                    appContext?.getString(R.string.source_production_company) ?: "Production company"
                }
                list.add(
                    JikanSearchResult(
                        malId = id, title = name,
                        subtitle = companySubtitle,
                        type = MediaType.Movie, total = null, score = null,
                        isAdult = false,
                        imageUrl = imageUrl, year = null, source = "tmdb"
                    )
                )
            }
            list
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ── Ağ ─────────────────────────────────────────────────────────────────────

    private suspend fun executeGet(urlStr: String): String? = withContext(Dispatchers.IO) {
        // withTimeoutOrNull: coroutine düzeyinde güvenlik ağı (OkHttp timeout'una ek olarak)
        withTimeoutOrNull(9_000L) {
            try {
                val request = Request.Builder()
                    .url(urlStr)
                    .header("Accept", "application/json")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        response.body?.string()
                    } else {
                        Log.e(TAG, "HTTP ${response.code} for ${SensitiveUrlRedactor.redact(urlStr)}")
                        null
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "executeGet failed (${e.javaClass.simpleName}) — ${SensitiveUrlRedactor.redact(urlStr)}")
                null
            }
        }
    }
}
