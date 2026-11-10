package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import com.kitsugi.animelist.utils.*
import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.R

/**
 * İstatistik, inceleme, forum konuları ve aktivite verilerini çeken istemci.
 * [KitsugiMediaTabsClient]'dan bölünmüştür.
 */
class KitsugiMediaSocialClient {

    private companion object {
        val malTopicJsonCache = java.util.concurrent.ConcurrentHashMap<String, JSONObject>()
        val malTopicOpCache = java.util.concurrent.ConcurrentHashMap<Int, JSONObject>()
    }

    private val relationsClient = KitsugiMediaRelationsClient()

    private fun supportsAnimeSocial(mediaType: MediaType): Boolean =
        mediaType == MediaType.Anime || mediaType == MediaType.Manga

    private fun validRealMalId(id: Int?): Int? = id?.takeIf { it in 1 until 100_000_000 }

    private fun cachedSocialDetail(source: String, externalId: Int, mediaType: MediaType): KitsugiMediaDetail? {
        if (externalId <= 0) return null
        val byType = DetailCache.getMediaDetail(source, externalId, mediaType.name)
            ?.takeIf { it.type == null || it.type == mediaType }
        if (byType != null) return byType
        return DetailCache.getMediaDetail(source, externalId)
            ?.takeIf { it.type == null || it.type == mediaType }
    }

    /**
     * Kaynağın kendi kimlik alanını kullanarak eşleşen AniList öğesini çözer.
     * TMDB/Simkl/Kitsu/Shikimori/Bangumi ID'leri asla MAL veya AniList ID'si
     * olarak doğrudan kullanılmaz; yalnızca açık çapraz eşleme kabul edilir.
     */
    private fun aniListIdFromStableId(externalId: Int): Int? =
        externalId.takeIf { it in 100_000_001..199_999_999 }?.minus(100_000_000)

    private suspend fun resolveAniListMediaId(
        source: String,
        externalId: Int,
        mediaType: MediaType,
        tmdbId: Int? = null,
        realMalId: Int? = null
    ): Int? {
        if (!supportsAnimeSocial(mediaType) || externalId <= 0) return null
        val canonical = MalJikanMediaSupport.canonicalSource(source)
        val detail = cachedSocialDetail(source, externalId, mediaType)
        val simklCross = if (canonical == "simkl") {
            runCatching {
                KitsugiSimklDetailClient.resolveSimklCrossIds(
                    simklId = externalId,
                    mediaType = mediaType,
                    hintTmdbId = tmdbId ?: detail?.tmdbId,
                    hintMalId = realMalId?.takeIf { it != externalId } ?: detail?.realMalId
                )
            }.getOrNull()
        } else null
        val safeMalId = validRealMalId(realMalId?.takeIf { canonical != "simkl" || it != externalId })
            ?: validRealMalId(detail?.realMalId)
            ?: validRealMalId(simklCross?.malId)
        val safeTmdbId = tmdbId?.takeIf { it > 0 }
            ?: detail?.tmdbId?.takeIf { it > 0 }
            ?: simklCross?.tmdbId?.takeIf { it > 0 }
            ?: externalId.takeIf { canonical == "tmdb" && it > 0 }

        val resolved = when (canonical) {
            "anilist" -> aniListIdFromStableId(externalId)
                ?: (safeMalId ?: validRealMalId(externalId))?.let {
                    runCatching { relationsClient.resolveAniListId(it, mediaType) }.getOrNull()
                }
            "jikan", "mal" -> {
                val malId = MalJikanMediaSupport.resolveMalId(source, externalId, safeMalId)
                malId?.let { runCatching { relationsClient.resolveAniListId(it, mediaType) }.getOrNull() }
            }
            "kitsu" -> {
                val kitsuId = KitsuIdNamespace.rawIdFromStable(externalId)
                val mapped = runCatching {
                    KitsugiIdResolver.resolveIds(
                        malId = safeMalId,
                        aniListId = null,
                        mediaType = mediaType,
                        kitsuId = kitsuId
                    ).aniListId
                }.getOrNull()
                mapped ?: safeMalId?.let { runCatching { relationsClient.resolveAniListId(it, mediaType) }.getOrNull() }
            }
            "shikimori" -> {
                val malId = resolveMalIdForShikimori(externalId, safeMalId, mediaType)
                malId?.let { runCatching { relationsClient.resolveAniListId(it, mediaType) }.getOrNull() }
            }
            "bangumi" -> {
                val cross = runCatching { KitsugiBangumiDetailClient.resolveCrossIds(externalId, mediaType) }.getOrNull()
                cross?.aniListId?.takeIf { it > 0 }
                    ?: validRealMalId(cross?.malId)?.let { runCatching { relationsClient.resolveAniListId(it, mediaType) }.getOrNull() }
            }
            "tmdb", "simkl" -> {
                simklCross?.aniListId?.takeIf { it > 0 } ?: run {
                    val mapping = if (safeMalId != null || safeTmdbId != null) {
                        runCatching {
                            KitsugiIdResolver.resolveIds(
                                malId = safeMalId,
                                aniListId = null,
                                tmdbId = safeTmdbId,
                                mediaType = mediaType
                            )
                        }.getOrNull()
                    } else null
                    mapping?.aniListId?.takeIf { it > 0 }
                        ?: safeMalId?.let { runCatching { relationsClient.resolveAniListId(it, mediaType) }.getOrNull() }
                }
            }
            else -> safeMalId?.let { runCatching { relationsClient.resolveAniListId(it, mediaType) }.getOrNull() }
        }
        return resolved?.takeIf { it > 0 }
    }

    /** Resolves a verified MAL counterpart without ever interpreting another provider's ID as MAL. */
    private suspend fun resolveRealMalIdForSocial(
        source: String,
        externalId: Int,
        mediaType: MediaType,
        tmdbId: Int? = null,
        realMalId: Int? = null
    ): Int? {
        if (!supportsAnimeSocial(mediaType) || externalId <= 0) return null
        val canonical = MalJikanMediaSupport.canonicalSource(source)
        val detail = cachedSocialDetail(source, externalId, mediaType)
        val simklCross = if (canonical == "simkl") {
            runCatching {
                KitsugiSimklDetailClient.resolveSimklCrossIds(
                    simklId = externalId,
                    mediaType = mediaType,
                    hintTmdbId = tmdbId ?: detail?.tmdbId,
                    hintMalId = realMalId?.takeIf { it != externalId } ?: detail?.realMalId
                )
            }.getOrNull()
        } else null
        val safeMalId = validRealMalId(realMalId?.takeIf { canonical != "simkl" || it != externalId })
            ?: validRealMalId(detail?.realMalId)
            ?: validRealMalId(simklCross?.malId)
        val safeTmdbId = tmdbId?.takeIf { it > 0 }
            ?: detail?.tmdbId?.takeIf { it > 0 }
            ?: simklCross?.tmdbId?.takeIf { it > 0 }
            ?: externalId.takeIf { canonical == "tmdb" && it > 0 }

        return when (canonical) {
            "jikan", "mal" -> MalJikanMediaSupport.resolveMalId(source, externalId, safeMalId)
            "anilist" -> safeMalId
                ?: aniListIdFromStableId(externalId)?.let { aniListId ->
                    runCatching {
                        KitsugiIdResolver.resolveIds(aniListId = aniListId, malId = null, mediaType = mediaType).malId
                    }.getOrNull()?.let(::validRealMalId)
                }
                ?: validRealMalId(externalId)
            "kitsu" -> safeMalId ?: runCatching {
                KitsugiIdResolver.resolveIds(
                    malId = null,
                    aniListId = null,
                    mediaType = mediaType,
                    kitsuId = KitsuIdNamespace.rawIdFromStable(externalId)
                ).malId
            }.getOrNull()?.let(::validRealMalId)
            "shikimori" -> resolveMalIdForShikimori(externalId, safeMalId, mediaType)
            "bangumi" -> {
                val crossMalId = runCatching {
                    KitsugiBangumiDetailClient.resolveCrossIds(externalId, mediaType).malId
                }.getOrNull()
                validRealMalId(crossMalId) ?: safeMalId
            }
            "tmdb", "simkl" -> safeMalId ?: safeTmdbId?.let { verifiedTmdbId ->
                runCatching {
                    KitsugiIdResolver.resolveIds(
                        malId = null,
                        aniListId = simklCross?.aniListId,
                        tmdbId = verifiedTmdbId,
                        mediaType = mediaType
                    ).malId
                }.getOrNull()?.let(::validRealMalId)
            }
            else -> safeMalId
        }
    }

    /**
     * Shikimori kayıtları için MAL ID'sini çözer (verilen değer → detay önbelleği → ARM →
     * Shikimori API). Shikimori ID'si ASLA MAL ID yerine kullanılmaz; eşleme yoksa null döner.
     */
    private fun resolveMalIdForShikimori(
        shikimoriId: Int,
        realMalId: Int?,
        mediaType: MediaType = MediaType.Anime
    ): Int? {
        validRealMalId(realMalId)?.let { return it }
        validRealMalId(cachedSocialDetail("shikimori", shikimoriId, mediaType)?.realMalId)?.let { return it }
        // The legacy Shikimori lookup probes anime before manga and caches by numeric ID
        // only. Do not use that ambiguous fallback for manga social content.
        if (mediaType == MediaType.Manga) return null
        return KitsugiIdResolver.resolveMalIdFromShikimori(shikimoriId)?.let(::validRealMalId)
    }

    /** Shikimori kaydı için AniList "stableId"si (100_000_000 + aniListId); yoksa null. */
    private suspend fun resolveAniListStableIdForShikimori(malId: Int?): Int? {
        if (malId == null || malId <= 0) return null
        val resolved = runCatching {
            KitsugiIdResolver.resolveIds(malId = malId, aniListId = null, tmdbId = null)
        }.getOrNull() ?: return null
        val aniListId = resolved.aniListId ?: return null
        return if (aniListId > 0) 100_000_000 + aniListId else null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Stats
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun fetchStats(
        source: String,
        externalId: Int?,
        mediaType: MediaType,
        realMalId: Int? = null,
        tmdbId: Int? = null
    ): KitsugiStats? {
        return withContext(Dispatchers.IO) {
            if (externalId == null || externalId <= 0) return@withContext null
            when (MalJikanMediaSupport.canonicalSource(source)) {
                "bangumi" -> {
                    // 1) Bangumi'nin kendi puan dağılımı + koleksiyon durumu + sıralaması (ek istek yok).
                    val native = runCatching { KitsugiBangumiDetailClient.fetchStats(externalId, mediaType) }.getOrNull()
                    val nativeUseful = native != null &&
                        (native.scoreDistribution.isNotEmpty() || native.rankings.isNotEmpty() ||
                            (native.completed ?: 0) + (native.watching ?: 0) + (native.planned ?: 0) > 0)
                    if (nativeUseful) return@withContext native

                    // 2) Yedek: Çözülen AniList / MAL kimliği (Bangumi stableId'si MAL ID DEĞİLDİR).
                    if (mediaType != MediaType.Anime && mediaType != MediaType.Manga) return@withContext native
                    val cross = runCatching { KitsugiBangumiDetailClient.resolveCrossIds(externalId, mediaType) }.getOrNull()
                    val malId = cross?.malId ?: KitsugiBangumiDetailClient.sanitizeMalId(realMalId)
                    val aniListStable = cross?.aniListStableId ?: resolveAniListStableIdForShikimori(malId)
                    if (aniListStable != null) {
                        val aniStats = fetchStatsFromAniList(aniListStable, mediaType)
                        if (aniStats != null && (aniStats.rankings.isNotEmpty() || aniStats.scoreDistribution.isNotEmpty())) {
                            return@withContext aniStats
                        }
                    }
                    if (malId != null && malId > 0) fetchStatsFromJikan(malId, mediaType) ?: native else native
                }
                "tmdb" -> {
                    val effectiveTmdbId = tmdbId ?: externalId
                    val isMovie = mediaType == MediaType.Movie
                    if (mediaType == MediaType.Anime) {
                        val malId = resolveRealMalIdForSocial("tmdb", effectiveTmdbId, mediaType, effectiveTmdbId, realMalId)
                        val aniId = resolveAniListMediaId("tmdb", effectiveTmdbId, mediaType, effectiveTmdbId, malId)
                        if (aniId != null && aniId > 0) {
                            val aniStats = fetchStatsFromAniList(100_000_000 + aniId, MediaType.Anime)
                            if (aniStats != null && (aniStats.scoreDistribution.isNotEmpty() || aniStats.rankings.isNotEmpty())) {
                                return@withContext aniStats
                            }
                        }
                        if (malId != null && malId > 0) {
                            val jikanStats = fetchStats("jikan", malId, MediaType.Anime, malId, effectiveTmdbId)
                            if (jikanStats != null) return@withContext jikanStats
                        }
                        TmdbApiClient().fetchStats(effectiveTmdbId, isMovie)
                    } else {
                        TmdbApiClient().fetchStats(effectiveTmdbId, isMovie)
                    }
                }
                "simkl" -> {
                    val simklCross = KitsugiSimklDetailClient.resolveSimklCrossIds(
                        simklId = externalId,
                        mediaType = mediaType,
                        hintTmdbId = tmdbId,
                        hintMalId = realMalId
                    )
                    val detail = cachedSocialDetail("simkl", externalId, mediaType)
                    val resolvedTmdb = tmdbId?.takeIf { it > 0 }
                        ?: simklCross.tmdbId
                        ?: detail?.tmdbId
                    val malId = realMalId?.takeIf { it > 0 && it != externalId }
                        ?: simklCross.malId
                        ?: detail?.realMalId
                    val aniListId = simklCross.aniListId
                        ?: if (supportsAnimeSocial(mediaType)) {
                            resolveAniListMediaId("simkl", externalId, mediaType, resolvedTmdb, malId)
                        } else null

                    if (supportsAnimeSocial(mediaType)) {
                        val animeType = if (mediaType == MediaType.Manga) MediaType.Manga else MediaType.Anime
                        val aniStats = if (aniListId != null && aniListId > 0) {
                            runCatching { fetchStatsFromAniList(100_000_000 + aniListId, animeType) }.getOrNull()
                        } else null
                        val jikanStats = if (malId != null && malId > 0) {
                            runCatching { fetchStatsFromJikan(malId, animeType) }.getOrNull()
                        } else null

                        if (jikanStats != null && aniStats != null) {
                            return@withContext jikanStats.copy(
                                rankings = aniStats.rankings.ifEmpty { jikanStats.rankings },
                                scoreDistribution = jikanStats.scoreDistribution.ifEmpty { aniStats.scoreDistribution }
                            )
                        }
                        if (aniStats != null && (aniStats.scoreDistribution.isNotEmpty() || aniStats.rankings.isNotEmpty())) {
                            return@withContext aniStats
                        }
                        if (jikanStats != null) {
                            return@withContext jikanStats
                        }
                    }

                    if (resolvedTmdb != null && resolvedTmdb > 0) {
                        val tmdbStats = runCatching {
                            TmdbApiClient().fetchStats(resolvedTmdb, simklCross.isMovie)
                        }.getOrNull()
                        if (tmdbStats != null) return@withContext tmdbStats
                    }

                    KitsugiSimklDetailClient.fetchSimklFallbackStats(externalId, mediaType)
                }
                "kitsu" -> {
                    val kitsuOffset = 300_000_000
                    val kitsuNumericId = if (externalId >= kitsuOffset) externalId - kitsuOffset else externalId
                    if (kitsuNumericId <= 0) return@withContext null

                    val resolved = runCatching {
                        KitsugiIdResolver.resolveIds(malId = realMalId, aniListId = null, kitsuId = kitsuNumericId)
                    }.getOrNull()

                    val aniListId = resolved?.aniListId
                    if (aniListId != null && aniListId > 0) {
                        val encoded = 100_000_000 + aniListId
                        val stats = fetchStatsFromAniList(encoded, mediaType)
                        if (stats != null) return@withContext stats
                    }

                    val jikanId = realMalId?.takeIf { it > 0 } ?: resolved?.malId?.takeIf { it > 0 }
                    if (jikanId != null) {
                        return@withContext fetchStatsFromJikan(jikanId, mediaType)
                    }

                    null
                }
                "jikan", "mal" -> {
                    val malId = MalJikanMediaSupport.resolveMalId(source, externalId, realMalId)
                        ?: return@withContext null
                    // MAL details use Jikan as their primary source. AniList is a fallback,
                    // not a prerequisite that can keep valid MAL statistics waiting.
                    val jikanStats = fetchStatsFromJikan(malId, mediaType)
                    if (jikanStats != null && (
                            jikanStats.watching != null ||
                                jikanStats.completed != null ||
                                jikanStats.planned != null ||
                                jikanStats.dropped != null ||
                                jikanStats.paused != null ||
                                jikanStats.scoreDistribution.isNotEmpty()
                            )
                    ) {
                        jikanStats
                    } else {
                        fetchStatsFromAniList(malId, mediaType)
                    }
                }
                "shikimori" -> {
                    // Detay yanıtı Shikimori'nin KENDİ puan/durum dağılımını zaten taşır.
                    // Ek servislere gerek kalmadan önce onu kullan; yalnızca gerçekten boşsa
                    // doğrulanmış MAL/AniList karşılığına düş. Shikimori ID'si asla MAL ID
                    // olarak yorumlanmaz.
                    val native = cachedSocialDetail("shikimori", externalId, mediaType)?.nativeStats
                    val nativeUseful = native != null && (
                        native.scoreDistribution.any { it.amount > 0 } ||
                            listOfNotNull(native.watching, native.completed, native.planned, native.dropped, native.paused)
                                .any { it > 0 }
                        )
                    if (nativeUseful) return@withContext native

                    val malId = resolveMalIdForShikimori(externalId, realMalId, mediaType)
                    val jikanType = if (mediaType == MediaType.Manga) MediaType.Manga else MediaType.Anime
                    if (malId != null && malId > 0) {
                        val aniListStableId = resolveAniListStableIdForShikimori(malId)
                        if (aniListStableId != null) {
                            val aniStats = fetchStatsFromAniList(aniListStableId, jikanType)
                            if (aniStats != null && (aniStats.rankings.isNotEmpty() || aniStats.scoreDistribution.isNotEmpty())) {
                                return@withContext aniStats
                            }
                        }
                        fetchStatsFromJikan(malId, jikanType) ?: native
                    } else native
                }
                "anilist"      -> fetchStatsFromAniList(externalId, mediaType)
                else           -> null
            }
        }
    }

    private suspend fun fetchStatsFromJikan(externalId: Int, mediaType: MediaType): KitsugiStats? {
        val endpoint = MalJikanMediaSupport.jikanEndpoint(mediaType)
        val url = java.net.URL("https://api.jikan.moe/v4/$endpoint/$externalId/statistics")
        return runCatching {
            KitsugiApiBase.runWithRateLimit {
                val response = KitsugiApiBase.executeGetRequestResilient(url) ?: return@runWithRateLimit null
                val root = JSONObject(response)
                val data = root.optJSONObject("data") ?: return@runWithRateLimit null
                val watching   = data.optionalPositiveInt("watching")
                val completed  = data.optionalPositiveInt("completed")
                val onHold     = data.optionalPositiveInt("on_hold")
                val dropped    = data.optionalPositiveInt("dropped")
                val planToWatch = data.optionalPositiveInt("plan_to_watch")
                val scores = data.optJSONArray("scores")
                val scoreList = mutableListOf<KitsugiScoreStat>()
                if (scores != null) {
                    for (i in 0 until scores.length()) {
                        val s = scores.optJSONObject(i) ?: continue
                        scoreList.add(KitsugiScoreStat(s.optInt("score"), s.optInt("votes")))
                    }
                }
                KitsugiStats(
                    watching = watching,
                    completed = completed,
                    planned = planToWatch,
                    dropped = dropped,
                    paused = onHold,
                    scoreDistribution = scoreList
                )
            }
        }.getOrNull()
    }

    private suspend fun fetchStatsFromAniList(externalId: Int, mediaType: MediaType): KitsugiStats? {
        val aniListId = if (externalId >= 100_000_000) externalId - 100_000_000 else null
        val idParam  = if (aniListId != null) "\$id: Int" else "\$idMal: Int"
        val idFilter = if (aniListId != null) "id: \$id"  else "idMal: \$idMal"
        val query = """
            query (${'$'}type: MediaType, $idParam) {
                Media($idFilter, type: ${'$'}type) {
                    rankings {
                        id rank type format year season allTime context
                    }
                    stats {
                        statusDistribution { status amount }
                        scoreDistribution  { score amount }
                    }
                }
            }
        """.trimIndent()
        val variables = JSONObject().put("type", MalJikanMediaSupport.aniListMediaType(mediaType))
        if (aniListId != null) variables.put("id", aniListId) else variables.put("idMal", externalId)

        return runCatching {
            val response = KitsugiApiBase.executeAniListQuery(query, variables) ?: return@runCatching null
            val root = JSONObject(response)
            val mediaObj = root.optJSONObject("data")?.optJSONObject("Media") ?: return@runCatching null
            
            val rankingsArr = mediaObj.optJSONArray("rankings")
            val rankingsList = mutableListOf<KitsugiRanking>()
            if (rankingsArr != null) {
                for (i in 0 until rankingsArr.length()) {
                    val r = rankingsArr.optJSONObject(i) ?: continue
                    val rank = r.optInt("rank", 0)
                    if (rank <= 0) continue
                    val type = r.optNullableString("type").orEmpty()
                    val context = r.optNullableString("context").orEmpty()
                    val allTime = r.optBoolean("allTime", false)
                    val year = r.optionalPositiveInt("year")
                    val season = r.optNullableString("season")
                    rankingsList.add(KitsugiRanking(rank = rank, type = type, context = context, allTime = allTime, year = year, season = season))
                }
            }

            val statsObj = mediaObj.optJSONObject("stats") ?: return@runCatching null
            val statusDist = statsObj.optJSONArray("statusDistribution")
            val scoreDist  = statsObj.optJSONArray("scoreDistribution")
            var watching: Int? = null; var completed: Int? = null
            var planned: Int? = null;  var dropped: Int? = null; var paused: Int? = null
            if (statusDist != null) {
                for (i in 0 until statusDist.length()) {
                    val item = statusDist.optJSONObject(i) ?: continue
                    when (item.optNullableString("status").orEmpty()) {
                        "CURRENT"   -> watching  = item.optInt("amount")
                        "COMPLETED" -> completed = item.optInt("amount")
                        "PLANNING"  -> planned   = item.optInt("amount")
                        "DROPPED"   -> dropped   = item.optInt("amount")
                        "PAUSED"    -> paused    = item.optInt("amount")
                    }
                }
            }
            val scoreList = mutableListOf<KitsugiScoreStat>()
            if (scoreDist != null) {
                for (i in 0 until scoreDist.length()) {
                    val item = scoreDist.optJSONObject(i) ?: continue
                    scoreList.add(KitsugiScoreStat(item.optInt("score"), item.optInt("amount")))
                }
            }
            KitsugiStats(
                watching = watching,
                completed = completed,
                planned = planned,
                dropped = dropped,
                paused = paused,
                scoreDistribution = scoreList,
                rankings = rankingsList
            )
        }.getOrNull()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Reviews — Tüm kaynakları harmanlayarak (Round-Robin Interleave) döndürür
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun fetchReviews(
        source: String,
        externalId: Int?,
        mediaType: MediaType,
        page: Int = 1,
        tmdbId: Int? = null,
        realMalId: Int? = null
    ): List<KitsugiReview> = withContext(Dispatchers.IO) {
        if (externalId == null || externalId <= 0) return@withContext emptyList()
        val canonical = MalJikanMediaSupport.canonicalSource(source)
        val animeSocialSupported = supportsAnimeSocial(mediaType)
        if (!animeSocialSupported && canonical !in setOf("bangumi", "simkl", "tmdb")) {
            return@withContext emptyList()
        }

        val detail = cachedSocialDetail(source, externalId, mediaType)
        val simklCross = if (canonical == "simkl") {
            runCatching {
                KitsugiSimklDetailClient.resolveSimklCrossIds(
                    simklId = externalId,
                    mediaType = mediaType,
                    hintTmdbId = tmdbId ?: detail?.tmdbId,
                    hintMalId = realMalId?.takeIf { it != externalId } ?: detail?.realMalId
                )
            }.getOrNull()
        } else null

        val resolvedTmdb = tmdbId?.takeIf { it > 0 }
            ?: detail?.tmdbId?.takeIf { it > 0 }
            ?: simklCross?.tmdbId?.takeIf { it > 0 }
            ?: externalId.takeIf { canonical == "tmdb" && it > 0 }

        val isMovieFormat = mediaType == MediaType.Movie || simklCross?.isMovie == true

        coroutineScope {
            val bangumiDeferred = async {
                if (canonical == "bangumi") {
                    runCatching { KitsugiBangumiDetailClient.fetchReviews(externalId, page) }
                        .getOrNull().orEmpty()
                } else emptyList()
            }

            val animeBlendedDeferred = async {
                if (animeSocialSupported) {
                    fetchAnimeSocialReviews(source, externalId, mediaType, page, resolvedTmdb, realMalId ?: simklCross?.malId, simklCross?.kitsuId)
                } else emptyList()
            }

            val tmdbDeferred = async {
                if (resolvedTmdb != null && resolvedTmdb > 0 && mediaType != MediaType.Manga) {
                    runCatching {
                        TmdbApiClient().fetchReviews(resolvedTmdb, isMovieFormat, page)
                    }.getOrNull().orEmpty()
                } else emptyList()
            }

            val bangumiList = bangumiDeferred.await()
            val animeBlended = animeBlendedDeferred.await()
            val tmdbList = tmdbDeferred.await()

            val bySource = LinkedHashMap<String, MutableList<KitsugiReview>>()
            for (rev in (bangumiList + animeBlended + tmdbList)) {
                bySource.getOrPut(rev.source.lowercase()) { mutableListOf() }.add(rev)
            }
            interleaveReviewLists(bySource.values.toList()).distinctBy(::reviewFingerprint)
        }
    }

    /**
     * Farklı kaynaklardan (AniList, MAL/Jikan, Kitsu, TMDB, Bangumi) gelen
     * inceleme listelerini round-robin (sırayla birer birer) harmanlar.
     * Böylece Yorumlar sekmesindeki ilk 5 kartta ve Tüm İncelemeler sayfasında tüm kaynaklar dengeli görünür.
     */
    private fun <T> interleaveReviewLists(lists: List<List<T>>): List<T> {
        val nonEmpty = lists.filter { it.isNotEmpty() }
        if (nonEmpty.isEmpty()) return emptyList()
        if (nonEmpty.size == 1) return nonEmpty.first()
        val maxLen = nonEmpty.maxOf { it.size }
        val result = ArrayList<T>(nonEmpty.sumOf { it.size })
        for (i in 0 until maxLen) {
            for (list in nonEmpty) {
                if (i < list.size) {
                    result.add(list[i])
                }
            }
        }
        return result
    }

    private suspend fun fetchAnimeSocialReviews(
        source: String,
        externalId: Int,
        mediaType: MediaType,
        page: Int,
        tmdbId: Int?,
        realMalId: Int?,
        hintKitsuId: Int? = null
    ): List<KitsugiReview> = coroutineScope {
        if (!supportsAnimeSocial(mediaType) || externalId <= 0) return@coroutineScope emptyList()
        val canonical = MalJikanMediaSupport.canonicalSource(source)
        val malId = resolveRealMalIdForSocial(source, externalId, mediaType, tmdbId, realMalId)
        val aniListId = resolveAniListMediaId(source, externalId, mediaType, tmdbId, realMalId)
        val kitsuId = hintKitsuId?.takeIf { it > 0 }
            ?: (if (canonical == "kitsu") KitsuIdNamespace.rawIdFromStable(externalId) else null)
            ?: runCatching {
                KitsugiIdResolver.resolveIds(
                    malId = malId,
                    aniListId = aniListId,
                    tmdbId = tmdbId,
                    mediaType = mediaType
                ).kitsuId
            }.getOrNull()?.takeIf { it > 0 }

        val aniDeferred = async {
            if (aniListId != null) {
                runCatching { fetchReviewsFromAniList(100_000_000 + aniListId, mediaType, page) }.getOrNull().orEmpty()
            } else if (malId != null) {
                runCatching { fetchReviewsFromAniList(malId, mediaType, page) }.getOrNull().orEmpty()
            } else emptyList()
        }
        val jikanDeferred = async {
            if (malId != null) {
                runCatching { fetchReviewsFromJikan(malId, mediaType, page) }.getOrNull().orEmpty()
            } else emptyList()
        }
        val kitsuDeferred = async {
            if (kitsuId != null && kitsuId > 0) {
                runCatching { fetchReviewsFromKitsu(kitsuId, mediaType, page) }.getOrNull().orEmpty()
            } else emptyList()
        }

        // Shikimori /topics inceleme değil, forum konusu döndürür. Bu veri bilinçli
        // olarak forum akışında gösterilir; MAL ID'si Shikimori ID'si varsayılarak
        // alakasız konuların inceleme kartına düşmesine izin verilmez.
        interleaveReviewLists(
            listOf(
                aniDeferred.await(),
                jikanDeferred.await(),
                kitsuDeferred.await()
            )
        ).distinctBy(::reviewFingerprint)
    }

    private fun reviewFingerprint(review: KitsugiReview): String =
        review.username.lowercase().trim() + "_" + review.summary.take(20).lowercase().trim()

    /**
     * Kitsu JSON:API üzerinden hem uzun incelemeleri (`/reviews`) hem de kısa tepkileri (`/media-reactions`) çeker.
     */
    private suspend fun fetchReviewsFromKitsu(kitsuId: Int, mediaType: MediaType, page: Int): List<KitsugiReview> = coroutineScope {
        if (kitsuId <= 0) return@coroutineScope emptyList()
        val limit = 10
        val offset = ((page - 1).coerceAtLeast(0)) * limit
        val filterField = if (mediaType == MediaType.Manga) "mangaId" else "animeId"
        val mediaTypeParam = if (mediaType == MediaType.Manga) "Manga" else "Anime"

        val reactionsDeferred = async {
            runCatching {
                val url = java.net.URL(
                    "https://kitsu.io/api/edge/media-reactions?filter[$filterField]=$kitsuId&include=user&page[limit]=$limit&page[offset]=$offset&sort=-upVotesCount"
                )
                val response = KitsugiApiBase.executeGetRequestWithHeaders(
                    url,
                    mapOf("Accept" to "application/vnd.api+json")
                ) ?: return@runCatching emptyList<KitsugiReview>()
                val root = JSONObject(response)
                val data = root.optJSONArray("data") ?: return@runCatching emptyList<KitsugiReview>()
                val included = root.optJSONArray("included")
                val usersById = mutableMapOf<String, JSONObject>()
                if (included != null) {
                    for (i in 0 until included.length()) {
                        val inc = included.optJSONObject(i) ?: continue
                        if (inc.optString("type") == "users") {
                            usersById[inc.optString("id")] = inc.optJSONObject("attributes") ?: JSONObject()
                        }
                    }
                }
                val list = mutableListOf<KitsugiReview>()
                for (i in 0 until data.length()) {
                    val item = data.optJSONObject(i) ?: continue
                    val attrs = item.optJSONObject("attributes") ?: continue
                    val text = attrs.optString("reaction", "").cleanApiText().trim()
                    if (text.isBlank()) continue
                    val upVotes = attrs.optInt("upVotesCount", 0)
                    val dateText = attrs.optNullableString("createdAt")?.take(10)
                    val userRelId = item.optJSONObject("relationships")
                        ?.optJSONObject("user")
                        ?.optJSONObject("data")
                        ?.optString("id")
                    val userAttrs = userRelId?.let { usersById[it] }
                    val username = userAttrs?.optNullableString("name")
                        ?: userAttrs?.optNullableString("slug")
                        ?: "Kitsu Kullanıcısı"
                    val avatarObj = userAttrs?.optJSONObject("avatar")
                    val avatarUrl = avatarObj?.optNullableString("medium")
                        ?: avatarObj?.optNullableString("small")
                        ?: avatarObj?.optNullableString("original")
                    val summary = if (text.length > 280) text.take(280) + "..." else text
                    list.add(
                        KitsugiReview(
                            id = item.optString("id").toIntOrNull(),
                            userId = userRelId?.toIntOrNull(),
                            username = username,
                            avatarUrl = avatarUrl,
                            score = null,
                            summary = summary,
                            fullText = text,
                            dateText = dateText,
                            helpfulCount = upVotes.takeIf { it > 0 },
                            source = "kitsu"
                        )
                    )
                }
                list
            }.getOrElse { emptyList() }
        }

        val reviewsDeferred = async {
            runCatching {
                val url = java.net.URL(
                    "https://kitsu.io/api/edge/reviews?filter[mediaId]=$kitsuId&filter[mediaType]=$mediaTypeParam&include=user&page[limit]=$limit&page[offset]=$offset&sort=-likesCount"
                )
                val response = KitsugiApiBase.executeGetRequestWithHeaders(
                    url,
                    mapOf("Accept" to "application/vnd.api+json")
                ) ?: return@runCatching emptyList<KitsugiReview>()
                val root = JSONObject(response)
                val data = root.optJSONArray("data") ?: return@runCatching emptyList<KitsugiReview>()
                val included = root.optJSONArray("included")
                val usersById = mutableMapOf<String, JSONObject>()
                if (included != null) {
                    for (i in 0 until included.length()) {
                        val inc = included.optJSONObject(i) ?: continue
                        if (inc.optString("type") == "users") {
                            usersById[inc.optString("id")] = inc.optJSONObject("attributes") ?: JSONObject()
                        }
                    }
                }
                val list = mutableListOf<KitsugiReview>()
                for (i in 0 until data.length()) {
                    val item = data.optJSONObject(i) ?: continue
                    val attrs = item.optJSONObject("attributes") ?: continue
                    val text = attrs.optString("content", "").cleanApiText().trim()
                    if (text.isBlank()) continue
                    val ratingRaw = attrs.optDouble("rating", 0.0)
                    val score10 = if (ratingRaw > 0.0) (ratingRaw / 2.0).toInt().coerceIn(1, 10) else null
                    val likes = attrs.optInt("likesCount", 0)
                    val dateText = attrs.optNullableString("createdAt")?.take(10)
                    val userRelId = item.optJSONObject("relationships")
                        ?.optJSONObject("user")
                        ?.optJSONObject("data")
                        ?.optString("id")
                    val userAttrs = userRelId?.let { usersById[it] }
                    val username = userAttrs?.optNullableString("name")
                        ?: userAttrs?.optNullableString("slug")
                        ?: "Kitsu Kullanıcısı"
                    val avatarObj = userAttrs?.optJSONObject("avatar")
                    val avatarUrl = avatarObj?.optNullableString("medium")
                        ?: avatarObj?.optNullableString("small")
                        ?: avatarObj?.optNullableString("original")
                    val summary = if (text.length > 280) text.take(280) + "..." else text
                    list.add(
                        KitsugiReview(
                            id = item.optString("id").toIntOrNull(),
                            userId = userRelId?.toIntOrNull(),
                            username = username,
                            avatarUrl = avatarUrl,
                            score = score10,
                            summary = summary,
                            fullText = text,
                            dateText = dateText,
                            helpfulCount = likes.takeIf { it > 0 },
                            source = "kitsu"
                        )
                    )
                }
                list
            }.getOrElse { emptyList() }
        }

        (reviewsDeferred.await() + reactionsDeferred.await()).distinctBy(::reviewFingerprint)
    }

    /** Shikimori'nin yapıma bağlı konularını olduğu gibi FORUM verisi olarak okur. */
    private suspend fun fetchForumTopicsFromShikimori(
        shikimoriId: Int,
        mediaType: MediaType
    ): List<KitsugiForumTopic> {
        if (shikimoriId <= 0) return emptyList()
        val endpoint = if (mediaType == MediaType.Manga) "mangas" else "animes"
        return runCatching {
            val url = java.net.URL("https://shikimori.io/api/$endpoint/$shikimoriId/topics?limit=12")
            val response = KitsugiApiBase.executeGetRequestResilient(url) ?: return@runCatching emptyList()
            val arr = org.json.JSONArray(response)
            val list = mutableListOf<KitsugiForumTopic>()
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val topicId = item.optInt("id").takeIf { it > 0 } ?: continue
                val title = (item.optNullableString("topic_title") ?: item.optNullableString("title"))
                    ?.cleanApiText()?.trim().orEmpty()
                if (title.isBlank()) continue
                val rawBody = item.optNullableString("body")
                    ?.cleanShikimoriBbCode()
                    ?.cleanApiText()
                    ?.trim()
                    .orEmpty()
                val userObj = item.optJSONObject("user")
                val username = userObj?.optNullableString("nickname")
                    ?: KitsugiStrings.get(R.string.shikimori_kullanicisi)
                val avatarUrl = userObj?.optJSONObject("image")?.optNullableString("x160")
                    ?: userObj?.optNullableString("avatar")
                list.add(
                    KitsugiForumTopic(
                        id = topicId,
                        title = title,
                        commentCount = item.optInt("comments_count", 0).coerceAtLeast(0),
                        viewCount = item.optInt("views_count", 0).coerceAtLeast(0),
                        username = username,
                        avatarUrl = ShikimoriPosterResolver.absoluteUrl(avatarUrl),
                        dateText = formatIsoDateShort(item.optNullableString("created_at")),
                        userId = userObj?.optInt("id")?.takeIf { it > 0 },
                        source = "shikimori",
                        body = rawBody
                    )
                )
            }
            list
        }.getOrElse { emptyList() }
    }

    private suspend fun fetchReviewsFromJikan(externalId: Int, mediaType: MediaType, page: Int): List<KitsugiReview> {
        val endpoint = MalJikanMediaSupport.jikanEndpoint(mediaType)
        val url = java.net.URL("https://api.jikan.moe/v4/$endpoint/$externalId/reviews?page=$page")
        return runCatching {
            KitsugiApiBase.runWithRateLimit {
                val response = KitsugiApiBase.executeGetRequestResilient(url) ?: return@runWithRateLimit emptyList()
                val root = JSONObject(response)
                val data = root.optJSONArray("data") ?: return@runWithRateLimit emptyList()
                val list = mutableListOf<KitsugiReview>()
                for (i in 0 until data.length()) {
                    val item = data.optJSONObject(i) ?: continue
                    val score = item.optionalPositiveInt("score")
                    val reviewText = item.optNullableString("review")?.cleanApiText().orEmpty()
                    val summary = if (reviewText.length > 280) reviewText.take(280) + "..." else reviewText
                    val rawDate = item.optNullableString("date")
                    val dateText = rawDate?.let {
                        try {
                            val sdfIn = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                            val sdfOut = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
                            sdfOut.format(sdfIn.parse(it)!!)
                        } catch (_: Exception) { it.take(10) }
                    }
                    val reactions = item.optJSONObject("reactions")
                    val helpfulCount = reactions?.optInt("overall") ?: item.optInt("votes")
                    val userObj  = item.optJSONObject("user")
                    val username = userObj?.optNullableString("username") ?: KitsugiStrings.get(R.string.kullanici)
                    val avatarUrl = userObj?.optJSONObject("images")?.optJSONObject("jpg")?.optNullableString("image_url")
                    list.add(KitsugiReview(
                        id = null, username = username, avatarUrl = avatarUrl,
                        score = score, summary = summary, fullText = reviewText,
                        dateText = dateText, helpfulCount = if (helpfulCount > 0) helpfulCount else null,
                        ratingAmount = null, userRating = null, source = "jikan"
                    ))
                }
                list
            }
        }.getOrElse { emptyList() }
    }

    private suspend fun fetchReviewsFromAniList(externalId: Int, mediaType: MediaType, page: Int): List<KitsugiReview> {
        val aniListId = if (externalId >= 100_000_000) {
            externalId - 100_000_000
        } else {
            relationsClient.resolveAniListId(externalId, mediaType)
        }
        val idParam  = if (aniListId != null) "\$id: Int" else "\$idMal: Int"
        val idFilter = if (aniListId != null) "id: \$id"  else "idMal: \$idMal"
        val query = """
            query (${'$'}type: MediaType, $idParam, ${'$'}page: Int) {
                Media($idFilter, type: ${'$'}type) {
                    reviews(page: ${'$'}page, perPage: 10, sort: RATING_DESC) {
                        nodes {
                            id summary body score rating ratingAmount userRating createdAt
                            user { id name avatar { medium } }
                        }
                    }
                }
            }
        """.trimIndent()
        val variables = JSONObject()
            .put("type", MalJikanMediaSupport.aniListMediaType(mediaType))
            .put("page", page)
        if (aniListId != null) variables.put("id", aniListId) else variables.put("idMal", externalId)

        return runCatching {
            val response = KitsugiApiBase.executeAniListQuery(query, variables) ?: return@runCatching emptyList()
            val nodes = JSONObject(response)
                .optJSONObject("data")?.optJSONObject("Media")
                ?.optJSONObject("reviews")?.optJSONArray("nodes") ?: return@runCatching emptyList()
            val list = mutableListOf<KitsugiReview>()
            for (i in 0 until nodes.length()) {
                val node = nodes.optJSONObject(i) ?: continue
                val createdAt = node.optInt("createdAt")
                val dateText = if (createdAt > 0) {
                    try {
                        val sdf = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
                        sdf.format(java.util.Date(createdAt * 1000L))
                    } catch (_: Exception) { null }
                } else null
                val summary  = node.optNullableString("summary")?.cleanApiText().orEmpty()
                val body     = node.optNullableString("body")?.cleanApiText().orEmpty()
                val userObj  = node.optJSONObject("user")
                list.add(KitsugiReview(
                    id = node.optInt("id"),
                    userId = userObj?.optInt("id"),
                    username = userObj?.optNullableString("name") ?: KitsugiStrings.get(R.string.kullanici),
                    avatarUrl = userObj?.optJSONObject("avatar")?.optNullableString("medium"),
                    score = node.optionalPositiveInt("score"),
                    summary = summary,
                    fullText = if (body.isNotBlank()) body else summary,
                    dateText = dateText,
                    helpfulCount = node.optionalPositiveInt("rating"),
                    ratingAmount = node.optionalPositiveInt("ratingAmount"),
                    userRating   = node.optNullableString("userRating"),
                    source = "anilist"
                ))
            }
            list
        }.getOrElse { emptyList() }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Forum Topics (AniList + MAL/Jikan + Resmi MAL v2 API Zenginleştirmesi)
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun fetchForumTopics(
        source: String,
        externalId: Int,
        mediaType: MediaType,
        page: Int = 1,
        tmdbId: Int? = null,
        realMalId: Int? = null
    ): List<KitsugiForumTopic> = withContext(Dispatchers.IO) {
        if (!supportsAnimeSocial(mediaType) || externalId <= 0) return@withContext emptyList()

        val canonical = MalJikanMediaSupport.canonicalSource(source)
        val aniListId = resolveAniListMediaId(source, externalId, mediaType, tmdbId, realMalId)
        val malId = resolveRealMalIdForSocial(source, externalId, mediaType, tmdbId, realMalId)

        coroutineScope {
            val aniDeferred = async {
                aniListId?.let {
                    runCatching { fetchForumTopicsFromAniList(it, page.coerceAtLeast(1)) }.getOrNull().orEmpty()
                }.orEmpty()
            }
            val jikanDeferred = async {
                if (malId != null && page in 1..3) {
                    runCatching { fetchForumTopicsFromJikan(malId, mediaType, page) }.getOrNull().orEmpty()
                } else emptyList()
            }
            val shikimoriDeferred = async {
                if (canonical == "shikimori" && page == 1) {
                    runCatching { fetchForumTopicsFromShikimori(externalId, mediaType) }.getOrNull().orEmpty()
                } else emptyList()
            }
            val aniTopics = aniDeferred.await()
            val jikanTopics = jikanDeferred.await()
            val shikimoriTopics = shikimoriDeferred.await()
            interleaveReviewLists(listOf(aniTopics, jikanTopics, shikimoriTopics))
                .distinctBy { "${it.source}_${it.id}" }
                .distinctBy { it.title.lowercase().trim() }
                .take(40)
        }
    }

    private suspend fun fetchForumTopicsFromJikan(
        externalId: Int,
        mediaType: MediaType,
        page: Int = 1
    ): List<KitsugiForumTopic> = coroutineScope {
        val pathType = MalJikanMediaSupport.jikanEndpoint(mediaType)
        val filterParam = when (page) {
            1 -> "?filter=all"
            2 -> "?filter=other"
            3 -> "?filter=episode"
            else -> return@coroutineScope emptyList()
        }
        val url = java.net.URL("https://api.jikan.moe/v4/$pathType/$externalId/forum$filterParam")
        val rawTopics = KitsugiApiBase.runWithRateLimit {
            val response = KitsugiApiBase.executeGetRequestResilient(url) ?: return@runWithRateLimit emptyList()
            runCatching {
                val root = JSONObject(response)
                val data = root.optJSONArray("data") ?: return@runCatching emptyList()
                val parsed = mutableListOf<Pair<String, KitsugiForumTopic>>()
                for (i in 0 until data.length()) {
                    val item = data.optJSONObject(i) ?: continue
                    val topicId = item.optInt("mal_id", 0)
                    if (topicId <= 0) continue
                    val rawDate = item.optNullableString("date")
                    val lastCommentDate = item.optJSONObject("last_comment")?.optNullableString("date") ?: rawDate.orEmpty()
                    val formattedDate = formatIsoDateShort(rawDate)
                    val author = item.optNullableString("author_username")?.ifBlank { null } ?: "MAL Kullanıcısı"
                    val topic = KitsugiForumTopic(
                        id = topicId,
                        title = item.optString("title"),
                        commentCount = item.optInt("comments"),
                        viewCount = 0,
                        username = author,
                        avatarUrl = null,
                        dateText = formattedDate,
                        source = "jikan",
                        body = ""
                    )
                    parsed.add(lastCommentDate to topic)
                }
                parsed.sortedByDescending { it.first }.map { it.second }
            }.getOrElse { emptyList() }
        }

        if (rawTopics.isEmpty()) return@coroutineScope emptyList()

        // İlk sayfadaki en güncel MAL konularının OP gövdesini, anketini ve yazar avatarını
        // Resmi MAL v2 Forum API'sinden paralel olarak zenginleştir.
        val enrichCount = if (page == 1) 6 else 4
        val sem = Semaphore(4)
        val enrichedTop = withTimeoutOrNull(3_500L) {
            rawTopics.take(enrichCount).map { topic ->
                async {
                    sem.withPermit {
                        runCatching { enrichMalForumTopic(topic) }.getOrDefault(topic)
                    }
                }
            }.awaitAll()
        } ?: rawTopics.take(enrichCount)

        enrichedTop + rawTopics.drop(enrichCount)
    }

    /**
     * Bir MAL forum konusunun OP (ilk mesaj) gövdesini, varsa anketini ve yazar avatarını
     * Resmi MyAnimeList v2 Forum API'si (`/v2/forum/topic/{id}`) üzerinden tamamlar.
     */
    suspend fun enrichForumTopicIfNeeded(topic: KitsugiForumTopic): KitsugiForumTopic = withContext(Dispatchers.IO) {
        if (!topic.source.equals("jikan", ignoreCase = true) && !topic.source.equals("mal", ignoreCase = true)) {
            return@withContext topic
        }
        if (topic.body.isNotBlank() && !topic.avatarUrl.isNullOrBlank()) {
            return@withContext topic
        }
        runCatching { enrichMalForumTopic(topic) }.getOrDefault(topic)
    }

    private suspend fun enrichMalForumTopic(topic: KitsugiForumTopic): KitsugiForumTopic {
        val dataObj = fetchMalTopicDataJson(topic.id, limit = 50, offset = 0) ?: return topic
        val posts = dataObj.optJSONArray("posts")
        val firstPost = if (posts != null && posts.length() > 0) posts.optJSONObject(0) else null
        val createdBy = firstPost?.optJSONObject("created_by")
        val avatarUrl = extractMalForumAvatar(createdBy) ?: topic.avatarUrl
        val userId = createdBy?.optInt("id", 0)?.takeIf { it > 0 } ?: topic.userId
        val username = createdBy?.optNullableString("name")?.takeIf { it.isNotBlank() } ?: topic.username
        val postBody = firstPost?.optNullableString("body")?.cleanApiText().orEmpty()
        val pollMarkdown = formatMalPollMarkdown(dataObj.optJSONObject("poll"))
        val combinedBody = listOf(pollMarkdown, postBody).filter { it.isNotBlank() }.joinToString("\n\n")
        val dateText = firstPost?.optNullableString("created_at")?.let(::formatIsoDateShort) ?: topic.dateText

        return topic.copy(
            username = username,
            avatarUrl = avatarUrl,
            userId = userId,
            dateText = dateText,
            body = combinedBody.ifBlank { topic.body }
        )
    }

    private fun extractMalForumAvatar(createdBy: JSONObject?): String? {
        if (createdBy == null) return null
        val raw = createdBy.optNullableString("forum_avator")
            ?: createdBy.optNullableString("forum_avatar")
            ?: createdBy.optNullableString("picture")
            ?: return null
        return when {
            raw.startsWith("http://", ignoreCase = true) -> "https://" + raw.removePrefix("http://")
            raw.startsWith("https://", ignoreCase = true) -> raw
            raw.startsWith("//") -> "https:$raw"
            else -> null
        }
    }

    private fun formatMalPollMarkdown(pollObj: JSONObject?): String {
        if (pollObj == null) return ""
        val question = pollObj.optString("question", "").trim()
        val options = pollObj.optJSONArray("options") ?: return ""
        if (options.length() == 0) return ""
        var totalVotes = 0
        for (i in 0 until options.length()) {
            totalVotes += options.optJSONObject(i)?.optInt("votes", 0) ?: 0
        }
        return buildString {
            append("📊 **Anket")
            if (question.isNotBlank()) append(": $question")
            append("**\n")
            for (i in 0 until options.length()) {
                val opt = options.optJSONObject(i) ?: continue
                val text = opt.optString("text", "").trim()
                val votes = opt.optInt("votes", 0)
                val pct = if (totalVotes > 0) (votes * 100.0 / totalVotes).toInt() else 0
                append("- **$text** — $votes oy (%$pct)\n")
            }
        }.trim()
    }

    private fun formatIsoDateShort(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return try {
            val clean = raw.take(19)
            val sdfIn = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
            val sdfOut = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
            sdfOut.format(sdfIn.parse(clean)!!)
        } catch (_: Exception) {
            raw.take(10)
        }
    }

    private fun formatIsoDateTime(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return try {
            val clean = raw.take(19)
            val sdfIn = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
            val sdfOut = java.text.SimpleDateFormat("dd MMM yyyy HH:mm", java.util.Locale.getDefault())
            sdfOut.format(sdfIn.parse(clean)!!)
        } catch (_: Exception) {
            raw.take(16).replace("T", " ")
        }
    }

    private suspend fun fetchMalTopicDataJson(topicId: Int, limit: Int = 50, offset: Int = 0): JSONObject? {
        if (topicId <= 0) return null
        val cacheKey = "$topicId:$offset"
        malTopicJsonCache[cacheKey]?.let { return it }
        if (offset == 0) {
            malTopicOpCache[topicId]?.let { cached ->
                val posts = cached.optJSONArray("posts")
                if (posts != null && posts.length() >= minOf(limit, 2)) {
                    return cached
                }
            }
        }

        val clientId = runCatching { com.kitsugi.animelist.BuildConfig.MAL_CLIENT_ID }.getOrNull().orEmpty()
        if (clientId.isBlank()) return null

        val safeLimit = limit.coerceIn(1, 100)
        val url = java.net.URL("https://api.myanimelist.net/v2/forum/topic/$topicId?limit=$safeLimit&offset=$offset")
        val response = runCatching {
            KitsugiApiBase.executeGetRequestWithHeaders(
                url,
                mapOf("X-MAL-CLIENT-ID" to clientId)
            )
        }.getOrNull() ?: return null

        return runCatching {
            val root = JSONObject(response)
            val dataObj = root.optJSONObject("data") ?: root
            if (dataObj.has("posts") || dataObj.has("title")) {
                malTopicJsonCache[cacheKey] = dataObj
                if (offset == 0) {
                    malTopicOpCache[topicId] = dataObj
                }
                dataObj
            } else null
        }.getOrNull()
    }

    private suspend fun fetchForumTopicsFromAniList(externalId: Int, page: Int): List<KitsugiForumTopic> {
        val aniListId = if (externalId >= 100_000_000) externalId - 100_000_000 else externalId
        val query = """
            query (${'$'}mediaId: Int, ${'$'}page: Int) {
                Page(page: ${'$'}page, perPage: 15) {
                    threads(mediaCategoryId: ${'$'}mediaId) {
                        id title body replyCount viewCount likeCount isLiked createdAt
                        user { id name avatar { medium } }
                    }
                }
            }
        """.trimIndent()
        val variables = JSONObject().put("mediaId", aniListId).put("page", page)
        return runCatching {
            val response = KitsugiApiBase.executeAniListQuery(query, variables) ?: return@runCatching emptyList()
            val threads = JSONObject(response).optJSONObject("data")?.optJSONObject("Page")?.optJSONArray("threads") ?: return@runCatching emptyList()
            val list = mutableListOf<KitsugiForumTopic>()
            for (i in 0 until threads.length()) {
                val item = threads.optJSONObject(i) ?: continue
                val createdAt = item.optInt("createdAt")
                val dateText = if (createdAt > 0) {
                    try { java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(createdAt * 1000L)) } catch (_: Exception) { null }
                } else null
                val userObj = item.optJSONObject("user")
                val bodyText = item.optNullableString("body")?.cleanApiText().orEmpty()
                list.add(KitsugiForumTopic(
                    id = item.optInt("id"), title = item.optString("title"),
                    commentCount = item.optInt("replyCount"), viewCount = item.optInt("viewCount"),
                    username = userObj?.optNullableString("name") ?: KitsugiStrings.get(R.string.kullanici),
                    avatarUrl = userObj?.optJSONObject("avatar")?.optNullableString("medium"),
                    dateText = dateText, likeCount = item.optInt("likeCount", 0),
                    isLiked = item.optBoolean("isLiked", false),
                    userId = userObj?.optInt("id"), source = "anilist",
                    body = bodyText
                ))
            }
            list
        }.getOrElse { emptyList() }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Activities
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun fetchActivities(
        source: String,
        externalId: Int,
        page: Int = 1,
        mediaType: MediaType = MediaType.Anime,
        tmdbId: Int? = null,
        realMalId: Int? = null
    ): List<KitsugiActivity> = withContext(Dispatchers.IO) {
        // AniList activity feed'i yalnızca Anime/Manga için anlamlıdır. Film/dizi
        // TMDB veya Simkl ID'lerini MAL/AniList namespace'ine düşürmek yasaktır.
        if (!supportsAnimeSocial(mediaType) || externalId <= 0) return@withContext emptyList()
        val aniListId = resolveAniListMediaId(source, externalId, mediaType, tmdbId, realMalId)
            ?: return@withContext emptyList()

        val query = """
            query (${'$'}mediaId: Int, ${'$'}page: Int) {
                Page(page: ${'$'}page, perPage: 15) {
                    activities(mediaId: ${'$'}mediaId, sort: [ID_DESC]) {
                        ... on ListActivity {
                            id status progress createdAt likeCount isLiked
                            user { id name avatar { medium } }
                            media { id type isAdult title { romaji english native } coverImage { large } }
                        }
                    }
                }
            }
        """.trimIndent()
        val variables = JSONObject().put("mediaId", aniListId).put("page", page.coerceAtLeast(1))
        runCatching {
            val response = KitsugiApiBase.executeAniListQuery(query, variables) ?: return@runCatching emptyList()
            val activities = JSONObject(response).optJSONObject("data")?.optJSONObject("Page")?.optJSONArray("activities")
                ?: return@runCatching emptyList()
            val expectedAniListType = if (mediaType == MediaType.Manga) "MANGA" else "ANIME"
            val list = mutableListOf<KitsugiActivity>()
            for (i in 0 until activities.length()) {
                val item = activities.optJSONObject(i) ?: continue
                val activity = parseActivity(item)
                if (activity.mediaId != aniListId || activity.mediaType?.equals(expectedAniListType, ignoreCase = true) != true) continue
                list.add(activity)
            }
            list
        }.getOrElse { emptyList() }
    }

    suspend fun fetchActivityReplies(activityId: Int): KitsugiActivity? {
        return withContext(Dispatchers.IO) {
            val query = """
                query (${'$'}activityId: Int) {
                    Activity(id: ${'$'}activityId) {
                        ... on ListActivity {
                            id status progress createdAt likeCount isLiked
                            user { id name avatar { medium } }
                            media { id type isAdult title { romaji english native } coverImage { large } }
                            replies { id text createdAt likeCount isLiked user { id name avatar { medium } } }
                        }
                        ... on TextActivity {
                            id text createdAt likeCount isLiked
                            user { id name avatar { medium } }
                            replies { id text createdAt likeCount isLiked user { id name avatar { medium } } }
                        }
                    }
                }
            """.trimIndent()
            val variables = JSONObject().put("activityId", activityId)
            runCatching {
                val response = KitsugiApiBase.executeAniListQuery(query, variables) ?: return@runCatching null
                val actObj = JSONObject(response).optJSONObject("data")?.optJSONObject("Activity") ?: return@runCatching null
                val base = parseActivity(actObj)
                val repliesArr = actObj.optJSONArray("replies")
                val repliesList = mutableListOf<KitsugiActivityReply>()
                if (repliesArr != null) {
                    for (i in 0 until repliesArr.length()) {
                        val rep = repliesArr.optJSONObject(i) ?: continue
                        val rCreatedAt = rep.optInt("createdAt")
                        val rDateText = if (rCreatedAt > 0) {
                            try { java.text.SimpleDateFormat("dd MMM yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(rCreatedAt * 1000L)) } catch (_: Exception) { null }
                        } else null
                        val rUser = rep.optJSONObject("user")
                        repliesList.add(KitsugiActivityReply(
                            id = rep.optInt("id"), text = rep.optString("text").cleanApiText(),
                            dateText = rDateText,
                            username = rUser?.optNullableString("name") ?: KitsugiStrings.get(R.string.kullanici),
                            avatarUrl = rUser?.optJSONObject("avatar")?.optNullableString("medium"),
                            likeCount = rep.optInt("likeCount", 0), isLiked = rep.optBoolean("isLiked", false),
                            userId = rUser?.optInt("id")
                        ))
                    }
                }
                base.copy(replies = repliesList)
            }.getOrNull()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Forum Replies (AniList GraphQL + Resmi MAL v2 Forum Topic API)
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun fetchForumTopicReplies(
        topicId: Int,
        page: Int = 1,
        source: String = "anilist"
    ): List<KitsugiForumReply> {
        return withContext(Dispatchers.IO) {
            if (source.equals("jikan", ignoreCase = true) || source.equals("mal", ignoreCase = true)) {
                return@withContext fetchMalForumTopicReplies(topicId, page)
            }
            if (source.equals("shikimori", ignoreCase = true)) {
                return@withContext fetchShikimoriTopicReplies(topicId, page)
            }
            val query = """
                query (${'$'}threadId: Int, ${'$'}page: Int) {
                    Page(page: ${'$'}page, perPage: 30) {
                        threadComments(threadId: ${'$'}threadId) {
                            id comment createdAt likeCount isLiked
                            user { id name avatar { medium } }
                            childComments
                        }
                    }
                }
            """.trimIndent()
            val variables = JSONObject().put("threadId", topicId).put("page", page)
            runCatching {
                val response = KitsugiApiBase.executeAniListQuery(query, variables) ?: return@runCatching emptyList()
                val comments = JSONObject(response).optJSONObject("data")?.optJSONObject("Page")?.optJSONArray("threadComments") ?: return@runCatching emptyList()
                val list = mutableListOf<KitsugiForumReply>()
                for (i in 0 until comments.length()) {
                    val item = comments.optJSONObject(i) ?: continue
                    val createdAt = item.optInt("createdAt")
                    val dateText = if (createdAt > 0) {
                        try { java.text.SimpleDateFormat("dd MMM yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(createdAt * 1000L)) } catch (_: Exception) { null }
                    } else null
                    val userObj = item.optJSONObject("user")
                    list.add(KitsugiForumReply(
                        id = item.optInt("id"), comment = item.optString("comment").cleanApiText(),
                        dateText = dateText,
                        username = userObj?.optNullableString("name") ?: KitsugiStrings.get(R.string.kullanici),
                        avatarUrl = userObj?.optJSONObject("avatar")?.optNullableString("medium"),
                        likeCount = item.optInt("likeCount", 0), isLiked = item.optBoolean("isLiked", false),
                        userId = userObj?.optInt("id"),
                        createdAt = createdAt,
                        childComments = parseChildComments(item.optJSONArray("childComments"))
                    ))
                }
                list
            }.getOrElse { emptyList() }
        }
    }

    /** Shikimori topic yorumları: /api/comments?commentable_type=Topic. */
    private suspend fun fetchShikimoriTopicReplies(
        topicId: Int,
        page: Int
    ): List<KitsugiForumReply> = withContext(Dispatchers.IO) {
        if (topicId <= 0) return@withContext emptyList()
        runCatching {
            val url = java.net.URL(
                "https://shikimori.io/api/comments?commentable_id=$topicId" +
                    "&commentable_type=Topic&page=${page.coerceAtLeast(1)}&limit=30&desc=0"
            )
            val response = KitsugiApiBase.executeGetRequestResilient(url)
                ?: return@runCatching emptyList()
            val array = org.json.JSONArray(response)
            val replies = mutableListOf<KitsugiForumReply>()
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                if (item.optInt("commentable_id") != topicId ||
                    !item.optString("commentable_type").equals("Topic", ignoreCase = true)
                ) continue
                val body = item.optNullableString("body")
                    ?.cleanShikimoriBbCode()
                    ?.cleanApiText()
                    ?.trim()
                    .orEmpty()
                if (body.isBlank()) continue
                val user = item.optJSONObject("user")
                val rawDate = item.optNullableString("created_at")
                val createdAt = rawDate?.let {
                    runCatching { java.time.OffsetDateTime.parse(it).toEpochSecond().toInt() }.getOrNull()
                }
                replies += KitsugiForumReply(
                    id = item.optInt("id").takeIf { it > 0 } ?: continue,
                    comment = body,
                    dateText = formatIsoDateTime(rawDate),
                    username = user?.optNullableString("nickname")
                        ?: KitsugiStrings.get(R.string.shikimori_kullanicisi),
                    avatarUrl = ShikimoriPosterResolver.absoluteUrl(
                        user?.optJSONObject("image")?.optNullableString("x160")
                            ?: user?.optNullableString("avatar")
                    ),
                    likeCount = 0,
                    isLiked = false,
                    userId = user?.optInt("id")?.takeIf { it > 0 },
                    createdAt = createdAt
                )
            }
            replies
        }.getOrElse { emptyList() }
    }

    /**
     * Resmi MyAnimeList v2 Forum API'sinden (`/v2/forum/topic/{topicId}`) konunun yanıtlarını çeker.
     * - Sayfa 1'de (`offset = 0`), `posts[0]` (`number == 1`) konunun ana mesajı (OP) olduğu için
     *   `malTopicOpCache`'e kaydedilir ve `posts[1..N]` yanıt listesi olarak döndürülür.
     *   Eğer konuda yalnızca 1 post varsa ve OP gövdesi boşsa veya yalnız yanıt olarak açıldıysa,
     *   kullanıcı boş ekran görmesin diye uygun şekilde işlenir.
     */
    private suspend fun fetchMalForumTopicReplies(topicId: Int, page: Int): List<KitsugiForumReply> {
        val pageSize = 50
        val offset = ((page - 1).coerceAtLeast(0)) * pageSize
        val dataObj = fetchMalTopicDataJson(topicId, limit = pageSize, offset = offset)
        if (dataObj != null) {
            val posts = dataObj.optJSONArray("posts")
            if (posts != null && posts.length() > 0) {
                val list = mutableListOf<KitsugiForumReply>()
                // Sayfa 1'de ilk post (number == 1) konunun ana mesajıdır (OP);
                // Konu Detayı başlığında zaten OP gövdesi olarak gösterilir.
                val firstNumber = posts.optJSONObject(0)?.optInt("number", 1) ?: 1
                val startIndex = if (page <= 1 && firstNumber == 1) 1 else 0
                for (i in startIndex until posts.length()) {
                    val post = posts.optJSONObject(i) ?: continue
                    val postId = post.optInt("id", 0).takeIf { it > 0 } ?: (topicId * 1000 + i)
                    val rawBody = post.optNullableString("body")?.cleanApiText()?.trim().orEmpty()
                    if (rawBody.isBlank()) continue
                    val createdBy = post.optJSONObject("created_by")
                    val username = createdBy?.optNullableString("name") ?: KitsugiStrings.get(R.string.mal_kullanicisi)
                    val userId = createdBy?.optInt("id", 0)?.takeIf { it > 0 }
                    val avatarUrl = extractMalForumAvatar(createdBy)
                    val dateText = formatIsoDateTime(post.optNullableString("created_at"))
                    list.add(
                        KitsugiForumReply(
                            id = postId,
                            comment = rawBody,
                            dateText = dateText,
                            username = username,
                            avatarUrl = avatarUrl,
                            likeCount = 0,
                            isLiked = false,
                            userId = userId,
                            createdAt = null,
                            childComments = emptyList()
                        )
                    )
                }
                return list
            }
        }
        return emptyList()
    }

    private fun parseChildComments(arr: org.json.JSONArray?): List<KitsugiForumReply> {
        if (arr == null) return emptyList()
        val list = mutableListOf<KitsugiForumReply>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val createdAt = item.optInt("createdAt")
            val dateText = if (createdAt > 0) {
                try { java.text.SimpleDateFormat("dd MMM yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(createdAt * 1000L)) } catch (_: Exception) { null }
            } else null
            val userObj = item.optJSONObject("user")
            list.add(KitsugiForumReply(
                id = item.optInt("id"), comment = item.optString("comment").cleanApiText(),
                dateText = dateText,
                username = userObj?.optNullableString("name") ?: KitsugiStrings.get(R.string.kullanici),
                avatarUrl = userObj?.optJSONObject("avatar")?.optNullableString("medium"),
                likeCount = item.optInt("likeCount", 0), isLiked = item.optBoolean("isLiked", false),
                userId = userObj?.optInt("id"),
                createdAt = createdAt,
                childComments = parseChildComments(item.optJSONArray("childComments"))
            ))
        }
        return list
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun parseActivity(item: JSONObject): KitsugiActivity {
        val createdAt = item.optInt("createdAt")
        val dateText = if (createdAt > 0) {
            try { java.text.SimpleDateFormat("dd MMM yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(createdAt * 1000L)) } catch (_: Exception) { null }
        } else null
        val userObj = item.optJSONObject("user")
        var mediaTitleRomaji: String? = null; var mediaTitleEnglish: String? = null
        var mediaTitleNative: String? = null;  var mediaCoverUrl: String? = null
        var mediaId: Int? = null; var mediaType: String? = null; var isAdult = false
        if (item.has("media")) {
            val mediaObj = item.optJSONObject("media")
            if (mediaObj != null) {
                mediaId           = mediaObj.optInt("id")
                mediaType         = mediaObj.optNullableString("type")
                isAdult           = mediaObj.optBoolean("isAdult", false)
                val titleObj      = mediaObj.optJSONObject("title")
                mediaTitleRomaji  = titleObj?.optNullableString("romaji")
                mediaTitleEnglish = titleObj?.optNullableString("english")
                mediaTitleNative  = titleObj?.optNullableString("native")
                mediaCoverUrl     = mediaObj.optJSONObject("coverImage")?.optNullableString("large")
            }
        }
        val mediaTitle = mediaTitleRomaji ?: mediaTitleEnglish ?: mediaTitleNative
        val text = if (item.has("text")) item.optString("text")
                   else formatActivityText(item.optNullableString("status"), item.optNullableString("progress"), mediaTitle)
        return KitsugiActivity(
            id = item.optInt("id"), text = text.cleanApiText(), dateText = dateText,
            username = userObj?.optNullableString("name") ?: KitsugiStrings.get(R.string.kullanici),
            avatarUrl = userObj?.optJSONObject("avatar")?.optNullableString("medium"),
            mediaTitle = mediaTitle, mediaTitleRomaji = mediaTitleRomaji,
            mediaTitleEnglish = mediaTitleEnglish, mediaTitleNative = mediaTitleNative,
            mediaCoverUrl = mediaCoverUrl,
            likeCount = item.optInt("likeCount", 0), isLiked = item.optBoolean("isLiked", false),
            mediaId = mediaId, mediaType = mediaType, isAdult = isAdult,
            userId = userObj?.optInt("id"), source = "anilist"
        )
    }

    private fun formatActivityText(status: String?, progress: String?, mediaTitle: String? = null): String {
        val cleanStatus   = status?.lowercase()?.trim().orEmpty()
        val cleanProgress = progress?.trim()?.takeIf { it.isNotBlank() && it != "null" }
        val prefix = if (!mediaTitle.isNullOrBlank()) "**$mediaTitle** " else ""
        return when {
            cleanStatus.contains("completed")    -> if (cleanProgress != null) "${prefix}serisini tamamladı ($cleanProgress)" else "${prefix}serisini tamamladı"
            cleanStatus.contains("watched")      -> if (cleanProgress != null) "${prefix}$cleanProgress. bölümü izledi" else "${prefix}izledi"
            cleanStatus.contains("watching")     -> if (cleanProgress != null) "${prefix}$cleanProgress. bölümü izliyor" else "${prefix}izliyor"
            cleanStatus.contains("plan")         -> if (cleanProgress != null) "${prefix}izlemeyi planlıyor ($cleanProgress)" else "${prefix}izlemeyi planlıyor"
            cleanStatus.contains("dropped")      -> if (cleanProgress != null) "${prefix}bıraktı ($cleanProgress)" else "${prefix}bıraktı"
            cleanStatus.contains("paused")       -> if (cleanProgress != null) "${prefix}ara verdi ($cleanProgress)" else "${prefix}ara verdi"
            cleanStatus.contains("rewatc")       -> if (cleanProgress != null) "${prefix}tekrar izledi ($cleanProgress)" else "${prefix}tekrar izledi"
            else -> if (cleanProgress != null) "${prefix}${status.orEmpty()} ($cleanProgress)" else "${prefix}${status.orEmpty()}"
        }
    }
}
