package com.kitsugi.animelist.data.trailer

import com.kitsugi.animelist.core.memory.BoundedCache

import android.content.Context
import android.util.Log
import com.kitsugi.animelist.data.cloudstream.CsStreamRunner
import com.kitsugi.animelist.data.cloudstream.CsTitleMatcher
import com.kitsugi.animelist.data.remote.KitsuClient
import com.kitsugi.animelist.data.remote.KitsugiAniListDetailClient
import com.kitsugi.animelist.data.remote.KitsugiMalDetailClient
import com.kitsugi.animelist.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URI
import java.util.Locale

private const val TAG = "DetailTrailerFallback"
private val YOUTUBE_VIDEO_ID_REGEX = Regex("^[a-zA-Z0-9_-]{11}$")

/**
 * Detay sayfaları için "YouTube fragmanı" yedek çözümleyici.
 *
 * Amaç: Kullanıcının detay sayfasını hangi kaynakdan (AniList, MAL/Jikan, Kitsu, Bangumi,
 * Shikimori, Simkl, TMDB...) açtığından bağımsız olarak fragman kartının dolmasıdır.
 *
 * Çözümleme zinciri (ilk bulan kazanır):
 *  1. **Kaynağın kendisi** — istemciler zaten `KitsugiMediaDetail.trailerUrl` doldurur;
 *     bu sınıf yalnızca o alan BOŞ olduğunda devreye girer (çağrı tarafı kontrol eder).
 *  2. **TMDB (diğer kaynaklar)** — [TrailerService.getExternalTrailerUrl] üzerinden
 *     TR-öncelikli YouTube fragman anahtarı (hızlı, ID bazlı).
 *  3. **Diğer metadata kaynakları** — AniList / Jikan(MAL) kimlik bazlı; Kitsu başlık araması
 *     ise başlık benzerliği ve yıl doğrulamasından geçerse kabul edilir.
 *  4. **Eklentiler (Cloudstream)** — başlıkla eklenti araması (`searchAllAddons`), en iyi
 *     eşleşmelerin `load()` yanıtındaki `trailers` alanından YouTube URL'si okunur.
 *
 * Tüm sonuçlar (bulunamayanlar dahil) bellek içi önbelleğe yazılır; aynı yapım için
 * detay sayfası her açılışında zincir yeniden koşulmaz.
 */
object DetailTrailerFallback {

    // key -> YouTube trailer URL (null = zincir boş döndü, negatif önbellek)
    private val cache = BoundedCache<String, String?>("trailer.fallback", 300)

    /**
     * Verilen kimlikler için YouTube fragman URL'si çözümler; bulunamazsa null.
     * Asla exception fırlatmaz — fragman eksikliği detay sayfasını bozmamalıdır.
     */
    suspend fun resolve(
        context: Context,
        title: String?,
        year: Int? = null,
        type: MediaType? = null,
        tmdbId: Int? = null,
        malId: Int? = null,
        aniListId: Int? = null,
        sourceName: String? = null
    ): String? {
        val cleanTitle = title?.trim().orEmpty()
        if (cleanTitle.isBlank()) return null

        val key = buildString {
            append(cleanTitle.lowercase(Locale.ROOT))
            append('|').append(year ?: 0)
            append('|').append(tmdbId ?: 0)
            append('|').append(malId ?: 0)
            append('|').append(aniListId ?: 0)
        }
        if (cache.containsKey(key)) {
            val cached = cache[key]
            Log.d(TAG, "Cache hit ($key): ${cached != null}")
            return cached
        }

        val found = try {
            resolveInternal(
                context = context,
                title = cleanTitle,
                year = year,
                type = type,
                tmdbId = tmdbId,
                malId = malId,
                aniListId = aniListId,
                sourceName = sourceName
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "resolve failed for '$cleanTitle': ${e.message}")
            null
        }

        cache[key] = found
        Log.i(TAG, "Fallback trailer for '$cleanTitle': ${found ?: "YOK"}")
        return found
    }

    /** Önbelleği temizler (ayar değişimi / test için). */
    fun clearCache() = cache.clear()

    // ─── Zincir ───────────────────────────────────────────────────────────────

    private suspend fun resolveInternal(
        context: Context,
        title: String,
        year: Int?,
        type: MediaType?,
        tmdbId: Int?,
        malId: Int?,
        aniListId: Int?,
        sourceName: String?
    ): String? {
        val typeKey = when (type) {
            MediaType.Movie -> "movie"
            MediaType.Anime, MediaType.TvShow -> "tv"
            else -> null
        }

        // ── 2) TMDB (diğer kaynaklar) — hızlı, ID bazlı ───────────────────────
        if (tmdbId != null && tmdbId > 0) {
            val fromTmdb = runCatching {
                TrailerServiceHolder.get(context)
                    .getExternalTrailerUrl(tmdbId.toString(), typeKey)
            }.getOrNull()
            if (isYouTubeTrailer(fromTmdb)) {
                Log.d(TAG, "TMDB trailer bulundu (tmdbId=$tmdbId)")
                return fromTmdb
            }
        }

        // ── 3) Diğer metadata kaynakları — kimlik bazlı paralel sondaj ───────
        crossSourceProbe(
            title = title,
            year = year,
            type = type,
            malId = malId,
            aniListId = aniListId,
            sourceName = sourceName
        )?.let {
            Log.d(TAG, "Çapraz kaynak trailer bulundu")
            return it
        }

        // ── 4) Eklentiler (Cloudstream) — başlık araması + load() trailers ────
        extensionProbe(context, title, year, type)?.let {
            Log.d(TAG, "Eklenti trailer bulundu")
            return it
        }

        Log.w(TAG, "Hiçbir kaynakta fragman bulunamadı: '$title' ($year)")
        return null
    }

    /**
     * AniList / Jikan(MAL) kimlik bazlı, Kitsu ise doğrulamalı başlık bazlı fragman sondajı.
     * Kitsu'nun `filter[text]` uç noktası en fazla bir sonuç verdiğinden, sonuç başlığı ve
     * bilinen yıl doğrulanmadan fragman kabul edilmez.
     */
    private suspend fun crossSourceProbe(
        title: String,
        year: Int?,
        type: MediaType?,
        malId: Int?,
        aniListId: Int?,
        sourceName: String?
    ): String? = withTimeoutOrNull(9_000L) {
        val mediaType = type ?: MediaType.Anime
        val source = sourceName?.lowercase(Locale.ROOT).orEmpty()
        // 100M+ değerler AniList stable-id'sidir, 300M+ Kitsu, 500M+ Bangumi — gerçek MAL ID değil.
        val realMalId = malId?.takeIf { it in 1..99_999_999 }

        coroutineScope {
            val probes = mutableListOf<kotlinx.coroutines.Deferred<String?>>()

            if (aniListId != null && aniListId > 0 && aniListId < 100_000_000 && source != "anilist") {
                probes += async(Dispatchers.IO) {
                    runCatching {
                        KitsugiAniListDetailClient.fetchDetail(aniListId, mediaType)?.trailerUrl
                    }.getOrNull()
                }
            }
            if (realMalId != null && source != "jikan" && source != "mal") {
                probes += async(Dispatchers.IO) {
                    runCatching {
                        KitsugiMalDetailClient.fetchDetail(realMalId, mediaType)?.trailerUrl
                    }.getOrNull()
                }
            }
            if (source != "kitsu") {
                probes += async(Dispatchers.IO) {
                    runCatching {
                        val detail = KitsuClient.fetchAnimeDetailByTitle(title) ?: return@runCatching null
                        val candidateTitles = listOfNotNull(
                            detail.title,
                            detail.titleTurkish,
                            detail.titleEnglish,
                            detail.titleRomaji,
                            detail.titleJapanese,
                            detail.titleNative
                        ) + detail.synonyms
                        val bestTitleSimilarity = candidateTitles.maxOfOrNull { candidate ->
                            CsTitleMatcher.getBestTitleSimilarity(
                                candidateName = candidate,
                                mainTitle = title,
                                altTitles = emptyList(),
                                isMovie = type == MediaType.Movie
                            )
                        } ?: 0.0
                        val yearCompatible = year == null || detail.year == null ||
                            kotlin.math.abs(year - detail.year) <= 1
                        if (bestTitleSimilarity >= 0.82 && yearCompatible) detail.trailerUrl else null
                    }.getOrNull()
                }
            }

            probes.awaitAll()
                .firstOrNull { isYouTubeTrailer(it) }
        }
    }

    /**
     * Cloudstream eklentilerinde fragman sondajı: başlıkla global arama yapılır,
     * ilk birkaç eşleşmenin `load()` yanıtındaki [com.lagradost.cloudstream3.LoadResponse.trailers]
     * listesinden YouTube URL'si okunur. En pahalı adım olduğu için EN SON koşulur ve
     * sert bir zaman tavanı vardır.
     */
    private suspend fun extensionProbe(
        context: Context,
        title: String,
        year: Int?,
        type: MediaType?
    ): String? = withTimeoutOrNull(25_000L) {
        withContext(Dispatchers.IO) {
            val results = runCatching {
                CsStreamRunner.searchAllAddons(context, title)
            }.getOrElse { e ->
                Log.w(TAG, "searchAllAddons başarısız: ${e.message}")
                emptyList()
            }
            if (results.isEmpty()) return@withContext null

            // searchAllAddons returns provider search hits, not verified matches. Loading the
            // first hit's trailers used to attach an unrelated show's YouTube video to a detail
            // page (e.g. an anime trailer on a Turkish live-action series). Reuse the same strict
            // title/type/year/season matcher as playback and reject every low-confidence hit.
            val isMovie = when (type) {
                MediaType.Movie -> true
                MediaType.Anime, MediaType.TvShow -> false
                else -> null
            }
            val verifiedCandidates = results.filter { (_, response) ->
                CsTitleMatcher.findBestMatch(
                    results = listOf(response),
                    mainTitle = title,
                    altTitles = emptyList(),
                    targetYear = year,
                    targetSeason = if (isMovie == true) null else 1,
                    isMovie = isMovie
                ) != null
            }.take(3)

            for ((api, searchResponse) in verifiedCandidates) {
                val loaded = runCatching {
                    CsStreamRunner.safeLoad(api, searchResponse.url)
                }.getOrNull() ?: continue
                // Providers can return a plausible search card whose load URL resolves to a
                // different work. Validate the loaded page's own title before trusting its trailer.
                if (CsTitleMatcher.getBestTitleSimilarity(
                        candidateName = loaded.name,
                        mainTitle = title,
                        altTitles = emptyList(),
                        isMovie = isMovie
                    ) < 0.82
                ) continue

                val trailerUrl = runCatching { loaded.trailers }
                    .getOrNull()
                    ?.firstOrNull { isYouTubeTrailer(it.extractorUrl) }
                    ?.extractorUrl
                if (!trailerUrl.isNullOrBlank()) {
                    Log.d(TAG, "Eklenti trailer: [${api.name}] $trailerUrl")
                    return@withContext trailerUrl
                }
            }
            null
        }
    }

    // ─── Yardımcılar ──────────────────────────────────────────────────────────

    /**
     * URL'nin fragman kartının oynatabileceği bir YouTube adresi olup olmadığını doğrular
     * (watch / youtu.be / embed / shorts / bare 11 karakterli ID).
     */
    internal fun isYouTubeTrailer(url: String?): Boolean {
        val trimmed = url?.trim().orEmpty()
        if (trimmed.isBlank()) return false
        if (trimmed.matches(YOUTUBE_VIDEO_ID_REGEX)) return true
        return runCatching {
            val uri = URI(trimmed)
            val host = uri.host?.lowercase()?.removePrefix("www.").orEmpty()
            if (host != "youtu.be" && host != "youtube.com" && !host.endsWith(".youtube.com")) {
                return@runCatching false
            }
            val path = uri.path.orEmpty().trim('/')
            val query = uri.rawQuery.orEmpty()
            when {
                host == "youtu.be" -> path.substringBefore('/').matches(YOUTUBE_VIDEO_ID_REGEX)
                path.startsWith("watch") -> query.split("&")
                    .firstOrNull { it.startsWith("v=") }
                    ?.removePrefix("v=")
                    ?.matches(YOUTUBE_VIDEO_ID_REGEX) == true
                path.startsWith("embed") || path.startsWith("shorts") || path.startsWith("live") ->
                    path.split("/").getOrNull(1)?.matches(YOUTUBE_VIDEO_ID_REGEX) == true
                else -> false
            }
        }.getOrDefault(false)
    }
}
