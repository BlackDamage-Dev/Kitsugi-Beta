package com.kitsugi.animelist.data.remote

import android.util.Log
import com.kitsugi.animelist.R
import com.kitsugi.animelist.core.memory.BoundedCache
import com.kitsugi.animelist.data.auth.PlatformRateLimiter
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.utils.KitsugiStrings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.URL
import java.util.Locale

/**
 * Bilinen harici medya kimlikleri (MyList kayıtları veya önbellekten önceden bilinen eşleşmeler).
 */
data class KnownVideoMediaIds(
    val malId: Int? = null,
    val aniListId: Int? = null,
    val kitsuId: Int? = null,
    val tmdbId: Int? = null,
    val simklId: Int? = null,
    val shikimoriId: Int? = null,
    val bangumiId: Int? = null,
    val imdbId: String? = null
)

/**
 * Detay sayfalarındaki "Videolar" sekmesi için çok kaynaklı video toplayıcı.
 *
 * "Resimler" sekmesindeki ortak galeri mimarisiyle birebir aynı prensiple çalışır:
 * Hangi kaynaktan (AniList, MyAnimeList, TMDB, Shikimori, Kitsu, Simkl, Bangumi, Fandom...)
 * açılırsa açılsın çapraz kimlikleri çözer ve video verisi sunan tüm kaynaklardan
 * (AniList, MyAnimeList/Jikan, TMDB, Shikimori, Kitsu, Simkl, AnimeThemes) fragman, PV,
 * açılış/kapanış müzikleri ve klip videolarını paralel olarak toplayıp tekilleştirir.
 */
object KitsugiMediaVideoAggregator {

    private const val TAG = "MediaVideoAggregator"
    private const val PROVIDER_TIMEOUT_MS = 9_000L

    private data class ArmIds(
        val malId: Int? = null,
        val aniListId: Int? = null,
        val kitsuId: Int? = null,
        val tmdbId: Int? = null,
        val simklId: Int? = null,
        val imdbId: String? = null
    )

    private val armCache = BoundedCache<String, ArmIds>("video.armIds", 250)
    private val aniListVideoCache = BoundedCache<String, List<MediaVideoItem>>("video.anilist", 200)
    private val aniListTitleIdCache = BoundedCache<String, Pair<Int, Int?>>("video.anilistTitleIds", 150)
    private val kitsuVideoCache = BoundedCache<Int, List<MediaVideoItem>>("video.kitsu", 200)

    /**
     * Birincil detay yanıtından sıfır gecikmeli başlangıç video listesi üretir.
     */
    fun seedFromDetail(
        detail: KitsugiMediaDetail?,
        source: String,
        trailerSourceOverride: String? = null
    ): List<MediaVideoItem> {
        if (detail == null) return emptyList()
        val primarySource = canonicalVideoSource(source)
        val raw = mutableListOf<MediaVideoItem>()

        detail.trailerUrl?.trim()?.takeIf { it.isNotEmpty() }?.let { tUrl ->
            val explicitSource = canonicalVideoSource(trailerSourceOverride)
                .ifEmpty { canonicalVideoSource(detail.trailerSource) }
            val tSource = when {
                explicitSource.isNotEmpty() -> explicitSource
                primarySource in setOf("bangumi", "fandom") -> ""
                else -> primarySource
            }
            raw += MediaVideoItem(
                key = "seed_trailer_${tSource.ifEmpty { "ext" }}",
                title = KitsugiStrings.get(R.string.inline_preview_c9d9dd2),
                videoUrl = tUrl,
                category = MediaVideoCategory.TRAILER,
                source = tSource
            )
        }

        detail.openings.forEachIndexed { idx, theme ->
            val src = inferThemeSource(theme, primarySource)
            val category = if (src == "tmdb") {
                classifyTmdbSeedTheme(theme.label)
            } else {
                MediaVideoCategory.OPENING
            }
            raw += MediaVideoItem(
                key = "seed_op_${src}_$idx",
                title = theme.label,
                videoUrl = theme.videoUrl,
                category = category,
                source = src,
                alternateUrls = theme.alternateVideoUrls
            )
        }

        detail.endings.forEachIndexed { idx, theme ->
            val src = inferThemeSource(theme, primarySource)
            raw += MediaVideoItem(
                key = "seed_ed_${src}_$idx",
                title = theme.label,
                videoUrl = theme.videoUrl,
                category = MediaVideoCategory.ENDING,
                source = src,
                alternateUrls = theme.alternateVideoUrls
            )
        }

        return mergeAndOrderMediaVideos(raw, preferredSource = primarySource)
    }

    private fun inferThemeSource(theme: KitsugiTheme, fallbackSource: String): String {
        val explicit = canonicalVideoSource(theme.source)
        if (explicit.isNotEmpty()) return explicit
        if (theme.videoUrl?.contains("animethemes.moe", ignoreCase = true) == true) {
            return "animethemes"
        }
        return fallbackSource
    }

    private fun classifyTmdbSeedTheme(label: String): MediaVideoCategory {
        val lower = label.trim().lowercase(Locale.ROOT)
        return when {
            lower.startsWith("opening credits") || lower.startsWith("açılış jeneriği") -> MediaVideoCategory.OPENING
            lower.startsWith("trailer") || lower.startsWith("fragman") || lower.startsWith("teaser") -> MediaVideoCategory.TRAILER
            else -> MediaVideoCategory.EXTRA
        }
    }

    /**
     * Tüm kaynaklardan videoları paralel toplar, her kaynak tamamlandıkça [onProgress]
     * üzerinden ara birleşimi yayınlar ve nihai tekilleştirilmiş listeyi döndürür.
     */
    suspend fun aggregateVideos(
        id: Int,
        source: String,
        mediaType: MediaType,
        detail: KitsugiMediaDetail?,
        tmdbClient: TmdbApiClient,
        knownIds: KnownVideoMediaIds = KnownVideoMediaIds(),
        fallbackTitle: String? = null,
        seasonNumber: Int? = null,
        onProgress: ((List<MediaVideoItem>) -> Unit)? = null
    ): List<MediaVideoItem> = withContext(Dispatchers.IO) {
        val canonicalSrc = canonicalVideoSource(source)
        val effectiveSeason = seasonNumber ?: detail?.tmdbSeason

        val accumulated = ArrayList<MediaVideoItem>()
        accumulated += seedFromDetail(detail, canonicalSrc)
        val mutex = Mutex()

        suspend fun publishMerged(itemsToAdd: List<MediaVideoItem>) {
            if (itemsToAdd.isEmpty()) return
            val snapshot = mutex.withLock {
                accumulated += itemsToAdd
                mergeAndOrderMediaVideos(accumulated, preferredSource = canonicalSrc)
            }
            onProgress?.invoke(snapshot)
        }

        val titleCandidates = buildList {
            detail?.title?.let { add(it) }
            detail?.titleRomaji?.let { add(it) }
            detail?.titleEnglish?.let { add(it) }
            detail?.titleNative?.let { add(it) }
            detail?.titleJapanese?.let { add(it) }
            detail?.synonyms?.let { addAll(it) }
            fallbackTitle?.let { add(it) }
        }.map { it.trim() }.filter { it.isNotEmpty() }.distinct()

        val primaryTitle = titleCandidates.firstOrNull().orEmpty()
        val isAnimeContext = isAnimeCompatibleWork(mediaType, canonicalSrc, detail, knownIds)

        // ── 1. Kaynağa özgü ad alanından (namespace) başlangıç kimliklerini çöz ─────
        fun realMal(v: Int?): Int? = v?.takeIf { it in 1..99_999_999 }
        fun pos(v: Int?): Int? = v?.takeIf { it > 0 }

        var malId: Int? = realMal(knownIds.malId) ?: realMal(detail?.realMalId)
        var aniListId: Int? = pos(knownIds.aniListId)
        var kitsuId: Int? = pos(knownIds.kitsuId)
        var tmdbId: Int? = pos(knownIds.tmdbId) ?: pos(detail?.tmdbId)
        var simklId: Int? = pos(knownIds.simklId)
        var shikimoriId: Int? = pos(knownIds.shikimoriId)
        val bangumiRawId: Int? = pos(knownIds.bangumiId)
            ?: if (canonicalSrc == "bangumi") BangumiIdNamespace.rawIdOrNull(id) else null
        var imdbId: String? = knownIds.imdbId?.trim()?.takeIf { it.isNotEmpty() }

        when (canonicalSrc) {
            "mal" -> if (malId == null) malId = realMal(id)
            "anilist" -> {
                if (aniListId == null) {
                    aniListId = when {
                        id in 100_000_001..199_999_999 -> id - 100_000_000
                        else -> null
                    }
                }
                if (malId == null) malId = realMal(id)
            }
            "kitsu" -> {
                if (kitsuId == null) {
                    kitsuId = KitsuIdNamespace.rawIdFromStable(id) ?: pos(id)
                }
            }
            "shikimori" -> {
                if (shikimoriId == null) shikimoriId = pos(id)
                if (malId == null && shikimoriId != null) {
                    malId = realMal(KitsugiIdResolver.resolveMalIdFromShikimori(shikimoriId))
                }
            }
            "simkl" -> if (simklId == null) simklId = pos(id)
            "tmdb" -> if (tmdbId == null) tmdbId = pos(id)
        }

        // Simkl çapraz kimlikleri
        if (simklId != null && (malId == null || aniListId == null || tmdbId == null || kitsuId == null)) {
            runCatching {
                KitsugiSimklDetailClient.resolveSimklCrossIds(
                    simklId = simklId,
                    mediaType = mediaType,
                    hintTmdbId = tmdbId,
                    hintMalId = malId
                )
            }.getOrNull()?.let { cross ->
                if (malId == null) malId = realMal(cross.malId)
                if (tmdbId == null) tmdbId = pos(cross.tmdbId)
                if (aniListId == null) aniListId = pos(cross.aniListId)
                if (kitsuId == null) kitsuId = pos(cross.kitsuId)
                if (imdbId == null) imdbId = cross.imdbId?.takeIf { it.isNotBlank() }
            }
        }

        // Bangumi çapraz kimlikleri
        if (bangumiRawId != null && (malId == null || aniListId == null || tmdbId == null || kitsuId == null)) {
            runCatching {
                KitsugiBangumiDetailClient.resolveCrossIds(
                    stableOrRawId = bangumiRawId,
                    mediaType = mediaType
                )
            }.getOrNull()?.let { cross ->
                if (malId == null) malId = realMal(cross.malId)
                if (aniListId == null) aniListId = pos(cross.aniListId)
                if (tmdbId == null) tmdbId = pos(cross.tmdbId)
                if (kitsuId == null) kitsuId = pos(cross.kitsuId)
                if (imdbId == null) imdbId = cross.imdbId?.takeIf { it.isNotBlank() }
            }
        }

        // TMDB kaynağından açılan animelerde tüm anime katalog kimliklerini (ARM + Wikidata) çöz
        if (isAnimeContext && mediaType != MediaType.Manga && tmdbId != null &&
            malId == null && aniListId == null && kitsuId == null
        ) {
            runCatching {
                KitsugiIdResolver.resolveAllTmdbAnimeIds(
                    tmdbId = tmdbId,
                    mediaType = mediaType,
                    knownImdbId = imdbId
                )
            }.getOrNull()?.let { cands ->
                if (malId == null) malId = cands.malIds.firstOrNull()?.let { realMal(it) }
                if (aniListId == null) aniListId = cands.aniListIds.firstOrNull()?.let { pos(it) }
                if (kitsuId == null) kitsuId = cands.kitsuIds.firstOrNull()?.let { pos(it) }
                if (simklId == null) simklId = pos(cands.simklId)
                if (imdbId == null) imdbId = cands.imdbId?.takeIf { it.isNotBlank() }
            }
        }

        // Salt başlıkla açılan sayfalarda (ör. Fandom Wiki) doğrulamalı AniList başlık eşleştirmesi
        if ((isAnimeContext || mediaType == MediaType.Manga) &&
            malId == null && aniListId == null && kitsuId == null && titleCandidates.isNotEmpty()
        ) {
            resolveAniListIdsByTitle(titleCandidates, isManga = mediaType == MediaType.Manga)?.let { (alId, mId) ->
                aniListId = pos(alId)
                if (malId == null) malId = realMal(mId)
            }
        }

        // ARM üzerinden tek istekte (mal + anilist + kitsu + tmdb + simkl + imdb) eşleştirme
        if (isAnimeContext && mediaType != MediaType.Manga) {
            val armLookupPair: Pair<String, Int>? = when {
                malId != null -> "myanimelist" to malId
                aniListId != null -> "anilist" to aniListId
                kitsuId != null -> "kitsu" to kitsuId
                else -> null
            }
            if (armLookupPair != null) {
                fetchArmIds(armLookupPair.first, armLookupPair.second)?.let { arm ->
                    if (malId == null) malId = arm.malId
                    if (aniListId == null) aniListId = arm.aniListId
                    if (kitsuId == null) kitsuId = arm.kitsuId
                    if (tmdbId == null) tmdbId = arm.tmdbId
                    if (simklId == null) simklId = arm.simklId
                    if (imdbId == null) imdbId = arm.imdbId
                }
            }
        }

        // Eksik kalan kimlikler için merkezi KitsugiIdResolver
        val needsCentralResolver = when {
            mediaType == MediaType.Manga -> malId == null || aniListId == null
            isAnimeContext -> malId == null || aniListId == null || kitsuId == null || tmdbId == null
            else -> imdbId == null && tmdbId != null
        }
        if (needsCentralResolver && (malId != null || aniListId != null || kitsuId != null || tmdbId != null || bangumiRawId != null)) {
            runCatching {
                KitsugiIdResolver.resolveIds(
                    malId = if (isAnimeContext || mediaType == MediaType.Manga) malId else null,
                    aniListId = if (isAnimeContext || mediaType == MediaType.Manga) aniListId else null,
                    tmdbId = tmdbId,
                    mediaType = if (isAnimeContext && mediaType != MediaType.Manga) MediaType.Anime else mediaType,
                    kitsuId = if (isAnimeContext) kitsuId else null,
                    bangumiId = bangumiRawId
                )
            }.getOrNull()?.let { resolved ->
                if (isAnimeContext || mediaType == MediaType.Manga) {
                    if (malId == null) malId = realMal(resolved.malId)
                    if (aniListId == null) aniListId = pos(resolved.aniListId)
                    if (kitsuId == null) kitsuId = pos(resolved.kitsuId)
                }
                if (tmdbId == null) tmdbId = pos(resolved.tmdbId)
                if (imdbId == null) imdbId = resolved.imdbId?.takeIf { it.isNotBlank() }
            }
        }

        // Simkl kimliği hâlâ yoksa (ör. TMDB film/dizi veya ARM'da olmayan anime) harici ID ile çöz
        if (simklId == null && mediaType != MediaType.Manga) {
            simklId = runCatching {
                KitsugiSimklDetailClient.resolveSimklIdByExternalIds(
                    malId = malId,
                    aniListId = aniListId,
                    tmdbId = tmdbId,
                    imdbId = imdbId,
                    mediaType = mediaType
                )
            }.getOrNull()
        }

        val resolvedMalId = malId
        val resolvedAniListId = aniListId
        val resolvedKitsuId = kitsuId
        val resolvedTmdbId = tmdbId
        val resolvedSimklId = simklId
        val isAnimeFinal = isAnimeContext || (resolvedMalId != null && mediaType != MediaType.Manga)
        val resolvedShikimoriId = shikimoriId ?: if (isAnimeFinal && mediaType != MediaType.Manga && resolvedMalId != null) {
            KitsugiIdResolver.resolveShikimoriIdFromMal(resolvedMalId) ?: resolvedMalId
        } else null
        val isMovieGuess = mediaType == MediaType.Movie || detail?.type == MediaType.Movie

        // ── 2. Tüm video kaynaklarını paralel sorgula ──────────────────────────────
        supervisorScope {
            val tasks = buildList {
                // (1) AniList (Anime & Manga resmi YouTube fragmanı)
                if ((isAnimeFinal || mediaType == MediaType.Manga) && (resolvedAniListId != null || resolvedMalId != null)) {
                    add(async {
                        val items = safeProviderCall("anilist") {
                            fetchAniListVideos(
                                aniListId = resolvedAniListId,
                                malId = resolvedMalId,
                                isManga = mediaType == MediaType.Manga
                            )
                        }
                        publishMerged(items)
                    })
                }

                // (2) MyAnimeList / Jikan (/v4/anime/{id}/videos -> promo + music_videos)
                if (isAnimeFinal && mediaType != MediaType.Manga && resolvedMalId != null) {
                    add(async {
                        val items = safeProviderCall("mal") {
                            KitsugiMalDetailClient.fetchMalVideos(resolvedMalId)
                        }
                        publishMerged(items)
                    })
                }

                // (3) TMDB (/3/{movie|tv}/{id}/videos -> Trailer, Teaser, Opening Credits, Clip, Featurette...)
                if (mediaType != MediaType.Manga && resolvedTmdbId != null) {
                    add(async {
                        val items = safeProviderCall("tmdb") {
                            tmdbClient.fetchMediaVideoItems(
                                tmdbId = resolvedTmdbId,
                                isMovie = isMovieGuess,
                                expectedTitles = if (canonicalSrc == "tmdb") emptyList() else titleCandidates,
                                seasonNumber = effectiveSeason,
                                ignoreFeatureGate = (canonicalSrc == "tmdb")
                            )
                        }
                        publishMerged(items)
                    })
                }

                // (4) Shikimori (/api/animes/{id}/videos -> PV, Character Trailer, CM, OP, ED, Clip)
                if (isAnimeFinal && mediaType != MediaType.Manga && resolvedShikimoriId != null) {
                    add(async {
                        val items = safeProviderCall("shikimori") {
                            PlatformRateLimiter.acquire("shikimori")
                            KitsugiShikimoriClient.fetchShikimoriVideos(resolvedShikimoriId)
                                .mapIndexed { idx, (kind, theme) ->
                                    val category = when (kind.lowercase(Locale.ROOT)) {
                                        "op" -> MediaVideoCategory.OPENING
                                        "ed" -> MediaVideoCategory.ENDING
                                        "clip", "other" -> MediaVideoCategory.EXTRA
                                        else -> MediaVideoCategory.TRAILER
                                    }
                                    MediaVideoItem(
                                        key = "shikimori_${resolvedShikimoriId}_$idx",
                                        title = theme.label,
                                        videoUrl = theme.videoUrl,
                                        category = category,
                                        source = "shikimori"
                                    )
                                }
                        }
                        publishMerged(items)
                    })
                }

                // (5) Kitsu (/anime/{id}?fields[anime]=youtubeVideoId)
                if (isAnimeFinal && mediaType != MediaType.Manga && resolvedKitsuId != null) {
                    add(async {
                        val items = safeProviderCall("kitsu") {
                            fetchKitsuVideos(resolvedKitsuId)
                        }
                        publishMerged(items)
                    })
                }

                // (6) Simkl (extended=full -> trailers[])
                if (mediaType != MediaType.Manga && resolvedSimklId != null) {
                    add(async {
                        val items = safeProviderCall("simkl") {
                            val simklType = when {
                                isMovieGuess -> MediaType.Movie
                                mediaType == MediaType.TvShow && !isAnimeFinal -> MediaType.TvShow
                                else -> MediaType.Anime
                            }
                            KitsugiSimklDetailClient.fetchSimklVideos(resolvedSimklId, simklType)
                        }
                        publishMerged(items)
                    })
                }

                // (7) AnimeThemes (api.animethemes.moe -> OP & ED doğrudan .webm/.mp4 akışları)
                if (isAnimeFinal && mediaType != MediaType.Manga &&
                    (resolvedAniListId != null || resolvedMalId != null || resolvedKitsuId != null)
                ) {
                    add(async {
                        val items = safeProviderCall("animethemes") {
                            fetchAnimeThemesItems(
                                aniListId = resolvedAniListId,
                                malId = resolvedMalId,
                                kitsuId = resolvedKitsuId
                            )
                        }
                        publishMerged(items)
                    })
                }
            }

            tasks.awaitAll()
        }

        val finalMerged = mutex.withLock {
            mergeAndOrderMediaVideos(accumulated, preferredSource = canonicalSrc)
        }
        if (id > 0 && canonicalSrc.isNotEmpty() && finalMerged.isNotEmpty()) {
            DetailCache.putMediaVideos(canonicalSrc, id, finalMerged, effectiveSeason)
        }
        finalMerged
    }

    private suspend fun safeProviderCall(
        providerName: String,
        block: suspend () -> List<MediaVideoItem>
    ): List<MediaVideoItem> = try {
        withTimeoutOrNull(PROVIDER_TIMEOUT_MS) { block() }.orEmpty()
    } catch (ce: CancellationException) {
        throw ce
    } catch (e: Exception) {
        Log.w(TAG, "Provider '$providerName' video fetch failed: ${e.message}")
        emptyList()
    }

    private fun isAnimeCompatibleWork(
        mediaType: MediaType,
        canonicalSrc: String,
        detail: KitsugiMediaDetail?,
        knownIds: KnownVideoMediaIds
    ): Boolean {
        if (mediaType == MediaType.Anime) return true
        if (mediaType == MediaType.Manga) return false
        if (canonicalSrc in setOf("anilist", "mal", "shikimori", "kitsu", "bangumi", "animethemes")) return true
        if ((detail?.realMalId ?: knownIds.malId)?.let { it in 1..99_999_999 } == true) return true
        if ((knownIds.aniListId ?: 0) > 0 || (knownIds.kitsuId ?: 0) > 0 || (knownIds.shikimoriId ?: 0) > 0) return true

        val genres = detail?.genres.orEmpty()
        return genres.any { g ->
            val lower = g.lowercase(Locale.ROOT)
            lower.contains("animasyon") || lower.contains("animation") || lower.contains("anime")
        }
    }

    private suspend fun resolveAniListIdsByTitle(
        titleCandidates: List<String>,
        isManga: Boolean
    ): Pair<Int, Int?>? {
        val searchTitle = titleCandidates.firstOrNull { it.length >= 2 } ?: return null
        val typeEnum = if (isManga) "MANGA" else "ANIME"
        val cacheKey = "$typeEnum:${searchTitle.lowercase(Locale.ROOT)}"
        aniListTitleIdCache[cacheKey]?.let { cached ->
            return if (cached.first > 0) cached else null
        }

        val query = """
            query (${'$'}search: String) {
                Media(search: ${'$'}search, type: $typeEnum) {
                    id
                    idMal
                    title { romaji english native }
                    synonyms
                }
            }
        """.trimIndent()
        val vars = JSONObject().put("search", searchTitle)
        PlatformRateLimiter.acquire("anilist")
        val response = KitsugiApiBase.executeAniListQuery(query, vars) ?: return null
        val media = runCatching {
            JSONObject(response).optJSONObject("data")?.optJSONObject("Media")
        }.getOrNull() ?: return null

        val alId = media.optInt("id", 0)
        if (alId <= 0) return null
        val titleObj = media.optJSONObject("title")
        val romaji = titleObj?.optString("romaji", "")
        val english = titleObj?.optString("english", "")
        val native = titleObj?.optString("native", "")
        val syns = buildList {
            if (!native.isNullOrBlank()) add(native)
            media.optJSONArray("synonyms")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optString(i)?.takeIf { it.isNotBlank() }?.let { add(it) }
                }
            }
        }
        if (!TmdbArtworkIdentity.matches(titleCandidates, romaji, english, syns)) {
            aniListTitleIdCache[cacheKey] = 0 to null
            return null
        }
        val malId = media.optInt("idMal", 0).takeIf { it in 1..99_999_999 }
        val hit = alId to malId
        aniListTitleIdCache[cacheKey] = hit
        return hit
    }

    private suspend fun fetchArmIds(sourceParam: String, id: Int): ArmIds? {
        if (id <= 0) return null
        val cacheKey = "$sourceParam:$id"
        armCache[cacheKey]?.let { return it }

        val parsed = runCatching {
            val url = URL("https://arm.haglund.dev/api/v2/ids?source=$sourceParam&id=$id")
            val text = KitsugiApiBase.executeGetRequest(url) ?: return@runCatching null
            val json = JSONObject(text)
            ArmIds(
                malId = json.optInt("myanimelist", 0).takeIf { it in 1..99_999_999 },
                aniListId = json.optInt("anilist", 0).takeIf { it > 0 },
                kitsuId = json.optInt("kitsu", 0).takeIf { it > 0 },
                tmdbId = json.optInt("themoviedb", 0).takeIf { it > 0 },
                simklId = json.optInt("simkl", 0).takeIf { it > 0 },
                imdbId = json.optString("imdb", "").trim().takeIf { it.isNotEmpty() && it != "null" }
            )
        }.getOrNull()

        if (parsed != null) {
            armCache[cacheKey] = parsed
        }
        return parsed
    }

    private suspend fun fetchAniListVideos(
        aniListId: Int?,
        malId: Int?,
        isManga: Boolean
    ): List<MediaVideoItem> {
        val typeEnum = if (isManga) "MANGA" else "ANIME"
        val cacheKey = "$typeEnum:al=${aniListId ?: 0}:mal=${malId ?: 0}"
        aniListVideoCache[cacheKey]?.let { return it }

        val (query, vars) = when {
            aniListId != null && aniListId > 0 -> {
                val q = "query (\$id: Int) { Media(id: \$id, type: $typeEnum) { id trailer { id site } } }"
                q to JSONObject().put("id", aniListId)
            }
            malId != null && malId in 1..99_999_999 -> {
                val q = "query (\$idMal: Int) { Media(idMal: \$idMal, type: $typeEnum) { id trailer { id site } } }"
                q to JSONObject().put("idMal", malId)
            }
            else -> return emptyList()
        }

        PlatformRateLimiter.acquire("anilist")
        val response = KitsugiApiBase.executeAniListQuery(query, vars) ?: return emptyList()
        val trailerObj = runCatching {
            JSONObject(response).optJSONObject("data")?.optJSONObject("Media")?.optJSONObject("trailer")
        }.getOrNull() ?: return emptyList()

        val site = trailerObj.optString("site", "").trim()
        val rawId = trailerObj.optString("id", "").trim()
        if (!site.equals("youtube", ignoreCase = true) || rawId.isEmpty()) return emptyList()

        val ytId = extractCanonicalYouTubeId(rawId) ?: rawId
        val result = listOf(
            MediaVideoItem(
                key = "anilist_trailer_$ytId",
                title = KitsugiStrings.get(R.string.source_video_trailer),
                videoUrl = "https://www.youtube.com/watch?v=$ytId",
                category = MediaVideoCategory.TRAILER,
                source = "anilist"
            )
        )
        aniListVideoCache[cacheKey] = result
        return result
    }

    private suspend fun fetchKitsuVideos(kitsuId: Int): List<MediaVideoItem> {
        if (kitsuId <= 0) return emptyList()
        kitsuVideoCache[kitsuId]?.let { return it }

        PlatformRateLimiter.acquire("kitsu")
        for (base in KitsuApiHost.candidates()) {
            val url = runCatching {
                URL("$base/anime/$kitsuId?fields[anime]=youtubeVideoId")
            }.getOrNull() ?: continue
            val body = KitsugiApiBase.executeGetRequest(url) ?: continue
            val ytRaw = runCatching {
                JSONObject(body)
                    .optJSONObject("data")
                    ?.optJSONObject("attributes")
                    ?.optString("youtubeVideoId", "")
                    ?.trim()
            }.getOrNull().orEmpty()
            if (ytRaw.isNotEmpty() && ytRaw != "null") {
                val ytId = extractCanonicalYouTubeId(ytRaw) ?: ytRaw
                val list = listOf(
                    MediaVideoItem(
                        key = "kitsu_trailer_${kitsuId}_$ytId",
                        title = KitsugiStrings.get(R.string.source_video_trailer),
                        videoUrl = "https://www.youtube.com/watch?v=$ytId",
                        category = MediaVideoCategory.TRAILER,
                        source = "kitsu"
                    )
                )
                kitsuVideoCache[kitsuId] = list
                return list
            }
            break
        }
        return emptyList()
    }

    private suspend fun fetchAnimeThemesItems(
        aniListId: Int?,
        malId: Int?,
        kitsuId: Int?
    ): List<MediaVideoItem> {
        val lookupOrder = buildList {
            if (aniListId != null && aniListId > 0) add("AniList" to aniListId)
            if (malId != null && malId in 1..99_999_999) add("MyAnimeList" to malId)
            if (kitsuId != null && kitsuId > 0) add("Kitsu" to kitsuId)
        }
        for ((site, extId) in lookupOrder) {
            val (ops, eds) = KitsugiAnimeThemesClient.fetchAnimeThemes(extId, site)
            if (ops.isNotEmpty() || eds.isNotEmpty()) {
                val items = ArrayList<MediaVideoItem>(ops.size + eds.size)
                ops.forEachIndexed { idx, theme ->
                    items += MediaVideoItem(
                        key = "animethemes_op_${extId}_$idx",
                        title = theme.label,
                        videoUrl = theme.videoUrl,
                        category = MediaVideoCategory.OPENING,
                        source = "animethemes",
                        alternateUrls = theme.alternateVideoUrls
                    )
                }
                eds.forEachIndexed { idx, theme ->
                    items += MediaVideoItem(
                        key = "animethemes_ed_${extId}_$idx",
                        title = theme.label,
                        videoUrl = theme.videoUrl,
                        category = MediaVideoCategory.ENDING,
                        source = "animethemes",
                        alternateUrls = theme.alternateVideoUrls
                    )
                }
                return items
            }
        }
        return emptyList()
    }
}
