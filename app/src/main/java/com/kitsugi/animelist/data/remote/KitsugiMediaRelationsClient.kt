package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import com.kitsugi.animelist.utils.toTurkishRelationType
import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.R

/**
 * İki medya kimliği çözümleme ve ilişki çekme işlemlerini yürüten istemci.
 * [KitsugiMediaTabsClient]'dan bölünmüştür.
 */
class KitsugiMediaRelationsClient {

    /**
     * Verilen MAL id'sini AniList id'sine çevirir.
     * Önce ARM API, ardından GraphQL fallback kullanır.
     */
    internal suspend fun resolveAniListId(malId: Int, mediaType: MediaType): Int? {
        if (malId <= 0 || (mediaType != MediaType.Anime && mediaType != MediaType.Manga)) return null
        return withContext(Dispatchers.IO) {
            val armResolved = runCatching {
                KitsugiIdResolver.resolveIds(malId, null, mediaType = mediaType).aniListId
            }.getOrNull()
            if (armResolved != null && armResolved > 0) return@withContext armResolved

            val query = """
                query (${'$'}idMal: Int, ${'$'}type: MediaType) {
                    Media(idMal: ${'$'}idMal, type: ${'$'}type) {
                        id
                    }
                }
            """.trimIndent()
            val variables = JSONObject()
                .put("idMal", malId)
                .put("type", MalJikanMediaSupport.aniListMediaType(mediaType))
            runCatching {
                val response = KitsugiApiBase.executeAniListQuery(query, variables) ?: return@runCatching null
                val root = JSONObject(response)
                root.optJSONObject("data")?.optJSONObject("Media")?.optInt("id")
            }.getOrNull()
        }
    }

    suspend fun fetchRelations(
        source: String,
        externalId: Int?,
        mediaType: MediaType,
        tmdbId: Int? = null,
        realMalId: Int? = null,
        title: String? = null
    ): List<KitsugiRelation> {
        return withContext(Dispatchers.IO) {
            if (externalId == null || externalId <= 0) return@withContext emptyList()

            when (MalJikanMediaSupport.canonicalSource(source)) {
                "bangumi" -> {
                    // 1) Bangumi'nin KENDİ ilişki ucu (p1 /relations): ön/devam, yan hikaye, uyarlama ...
                    val native = runCatching { KitsugiBangumiDetailClient.fetchRelations(externalId, mediaType) }
                        .getOrNull().orEmpty()
                    if (native.isNotEmpty()) return@withContext native

                    // 2) Yedek: Çözülen MAL / AniList kimliği (Bangumi stableId'si MAL ID DEĞİLDİR).
                    val cross = runCatching { KitsugiBangumiDetailClient.resolveCrossIds(externalId, mediaType) }.getOrNull()
                    val malId = cross?.malId ?: KitsugiBangumiDetailClient.sanitizeMalId(realMalId)
                    if (mediaType == MediaType.Anime || mediaType == MediaType.Manga) {
                        if (malId != null && malId > 0) {
                            val list = fetchRelationsFromJikan(malId, mediaType)
                            if (list.isNotEmpty()) return@withContext list
                        }
                        val aniListStable = cross?.aniListStableId ?: resolveAniListStableIdForShikimori(malId)
                        if (aniListStable != null) fetchRelationsFromAniList(aniListStable, mediaType) else emptyList()
                    } else {
                        val resolvedTmdb = tmdbId ?: cross?.tmdbId
                        if (resolvedTmdb != null && resolvedTmdb > 0) {
                            TmdbApiClient().fetchRelations(resolvedTmdb, mediaType == MediaType.Movie)
                                .map { it.copy(source = "tmdb") }
                        } else emptyList()
                    }
                }
                "simkl" -> {
                    val simklCross = KitsugiSimklDetailClient.resolveSimklCrossIds(
                        simklId = externalId,
                        mediaType = mediaType,
                        hintTmdbId = tmdbId,
                        hintMalId = realMalId
                    )
                    val detail = DetailCache.getMediaDetail("simkl", externalId)
                    val malId = realMalId?.takeIf { it > 0 && it != externalId }
                        ?: simklCross.malId
                        ?: detail?.realMalId
                    val resolvedTmdb = tmdbId?.takeIf { it > 0 }
                        ?: simklCross.tmdbId
                        ?: detail?.tmdbId
                    val aniListId = simklCross.aniListId
                    val effectiveTitle = title ?: simklCross.title ?: detail?.title ?: detail?.titleEnglish
                    val animeMediaType = if (mediaType == MediaType.Manga) MediaType.Manga else MediaType.Anime

                    val combined = mutableListOf<KitsugiRelation>()

                    if (malId != null && malId > 0) {
                        val malList = runCatching {
                            fetchRelations("jikan", malId, animeMediaType, resolvedTmdb, malId, effectiveTitle)
                        }.getOrNull().orEmpty()
                        combined.addAll(malList)
                    } else if (aniListId != null && aniListId > 0) {
                        val aniList = runCatching {
                            fetchRelationsFromAniList(100_000_000 + aniListId, animeMediaType)
                        }.getOrNull().orEmpty()
                        combined.addAll(aniList)
                    }

                    // Simkl'in kendi relations dizisi (varsa)
                    val simklNative = runCatching {
                        KitsugiSimklDetailClient.fetchSimklNativeRelations(externalId, mediaType)
                    }.getOrNull().orEmpty()
                    if (simklNative.isNotEmpty()) {
                        val existingTitles = combined.mapNotNull {
                            it.title.lowercase().replace(Regex("[^a-z0-9]"), "").takeIf { s -> s.isNotBlank() }
                        }.toMutableSet()
                        for (rel in simklNative) {
                            val norm = rel.title.lowercase().replace(Regex("[^a-z0-9]"), "")
                            if (norm.isNotBlank() && existingTitles.add(norm)) {
                                combined.add(rel)
                            }
                        }
                    }

                    if (resolvedTmdb != null && resolvedTmdb > 0) {
                        val tmdbRelations = runCatching {
                            TmdbApiClient().fetchRelations(resolvedTmdb, simklCross.isMovie)
                        }.getOrNull().orEmpty()
                        if (tmdbRelations.isNotEmpty()) {
                            val existingTitles = combined.mapNotNull {
                                it.title.lowercase().replace(Regex("[^a-z0-9]"), "").takeIf { s -> s.isNotBlank() }
                            }.toMutableSet()
                            for (rel in tmdbRelations) {
                                val norm = rel.title.lowercase().replace(Regex("[^a-z0-9]"), "")
                                if (norm.isNotBlank() && existingTitles.add(norm)) {
                                    combined.add(rel.copy(source = "tmdb"))
                                }
                            }
                        }
                    }

                    if (combined.isEmpty() && mediaType == MediaType.Anime && !effectiveTitle.isNullOrBlank()) {
                        val cleanTitle = effectiveTitle.replace(Regex("\\s*\\(.*?\\)"), "").trim()
                        if (cleanTitle.isNotBlank()) {
                            combined.addAll(
                                runCatching { fetchRelationsFromAniListBySearch(cleanTitle, animeMediaType) }
                                    .getOrNull().orEmpty()
                            )
                        }
                    }

                    combined.distinctBy { "${it.source}_${it.malId}" }
                }
                "tmdb" -> {
                    val effectiveTmdbId = tmdbId ?: externalId
                    if (effectiveTmdbId > 0) {
                        val isMovie = mediaType == MediaType.Movie
                        val tmdbRels = TmdbApiClient().fetchRelations(effectiveTmdbId, isMovie).map { it.copy(source = "tmdb") }
                        if (tmdbRels.isNotEmpty() && mediaType != MediaType.Anime) return@withContext tmdbRels

                        val mediaDetail = DetailCache.getMediaDetail("tmdb", effectiveTmdbId)
                        val resolvedCross = runCatching {
                            KitsugiIdResolver.resolveIds(
                                malId = realMalId ?: mediaDetail?.realMalId,
                                aniListId = null,
                                tmdbId = effectiveTmdbId,
                                mediaType = mediaType
                            )
                        }.getOrNull()
                        val malId = realMalId ?: mediaDetail?.realMalId ?: resolvedCross?.malId
                        if (malId != null && malId > 0) {
                            val malList = fetchRelations("jikan", malId, MediaType.Anime, effectiveTmdbId, malId, title)
                            if (malList.isNotEmpty()) return@withContext (malList + tmdbRels).distinctBy { "${it.source}_${it.malId}" }
                        }
                        val aniListId = resolvedCross?.aniListId
                        if (aniListId != null && aniListId > 0) {
                            val aniList = fetchRelationsFromAniList(100_000_000 + aniListId, MediaType.Anime)
                            if (aniList.isNotEmpty()) return@withContext (aniList + tmdbRels).distinctBy { "${it.source}_${it.malId}" }
                        }

                        val cleanTitle = (title ?: mediaDetail?.title ?: mediaDetail?.titleEnglish)
                            ?.replace(Regex("\\s*\\(.*?\\)"), "")?.trim()
                        if (!cleanTitle.isNullOrBlank()) {
                            val aniRelations = fetchRelationsFromAniListBySearch(cleanTitle, mediaType)
                            if (aniRelations.isNotEmpty()) return@withContext (aniRelations + tmdbRels).distinctBy { "${it.source}_${it.malId}" }
                        }

                        tmdbRels
                    } else emptyList()
                }
                "kitsu" -> {
                    val kitsuOffset = 300_000_000
                    val kitsuNumericId = if (externalId >= kitsuOffset) externalId - kitsuOffset else externalId
                    if (kitsuNumericId <= 0) return@withContext emptyList()

                    val resolved = runCatching {
                        KitsugiIdResolver.resolveIds(malId = realMalId, aniListId = null, kitsuId = kitsuNumericId)
                    }.getOrNull()

                    val aniListId = resolved?.aniListId
                    if (aniListId != null && aniListId > 0) {
                        val encoded = 100_000_000 + aniListId
                        val list = fetchRelationsFromAniList(encoded, mediaType)
                        if (list.isNotEmpty()) return@withContext list
                    }

                    val jikanId = realMalId?.takeIf { it > 0 } ?: resolved?.malId?.takeIf { it > 0 }
                    if (jikanId != null) {
                        val list = fetchRelationsFromJikan(jikanId, mediaType)
                        if (list.isNotEmpty()) return@withContext list
                    }

                    emptyList()
                }
                "shikimori" -> {
                    // 1) Shikimori'nin KENDİ `related` ucu: MAL/ARM eşlemesi gerektirmez,
                    //    tek istek sürer ve sayfa açılır açılmaz dolar.
                    val native = runCatching {
                        KitsugiShikimoriClient.fetchRelatedRelations(externalId, mediaType)
                    }.getOrNull().orEmpty()
                    if (native.isNotEmpty()) return@withContext native

                    // 2) Yedek: gerçek MAL ID'si çözülüp Jikan (MAL) ilişkileri kullanılır.
                    //    MAL eşlemesi yoksa AniList (ARM ile çözülen AniList ID) denenir.
                    val malId = resolveMalIdForShikimori(externalId, realMalId)
                    if (malId != null && malId > 0) {
                        val list = fetchRelationsFromJikan(malId, if (mediaType == MediaType.Manga) MediaType.Manga else MediaType.Anime)
                        if (list.isNotEmpty()) return@withContext list
                    }
                    val aniListStableId = resolveAniListStableIdForShikimori(malId)
                    if (aniListStableId != null) fetchRelationsFromAniList(aniListStableId, mediaType) else emptyList()
                }
                "jikan", "mal" -> {
                    val malId = MalJikanMediaSupport.resolveMalId(source, externalId, realMalId)
                        ?: return@withContext emptyList()
                    val jikanRelations = fetchRelationsFromJikan(malId, mediaType)
                    if (jikanRelations.isNotEmpty()) {
                        // Jikan relations kapak görseli içermez (imageUrl = null) → AniList kapaklarıyla zenginleştir
                        val enriched = enrichJikanRelationsWithAniListCovers(jikanRelations, mediaType)
                        val aniListRelations = fetchRelationsFromAniList(malId, mediaType)
                        if (aniListRelations.isNotEmpty()) {
                            val jikanTitles = enriched.map { it.title.lowercase().trim() }.toSet()
                            val jikanIds = enriched.map { it.malId }.toSet()
                            val extra = aniListRelations.filter { rel ->
                                val t = rel.title.lowercase().trim()
                                rel.malId !in jikanIds && t !in jikanTitles && rel.imageUrl != null
                            }
                            val combined = (enriched + extra).distinctBy { it.malId }
                            return@withContext combined
                        }
                        return@withContext enriched
                    }

                    // Jikan boşsa AniList'e düş
                    val aniListRelations = fetchRelationsFromAniList(malId, mediaType)
                    if (aniListRelations.isNotEmpty()) return@withContext aniListRelations

                    if (mediaType != MediaType.Manga && tmdbId != null && tmdbId > 0) {
                        TmdbApiClient().fetchRelations(tmdbId, mediaType == MediaType.Movie)
                            .map { it.copy(source = "tmdb") }
                    } else {
                        emptyList()
                    }
                }
                "anilist"      -> fetchRelationsFromAniList(externalId, mediaType)
                else           -> emptyList()
            }
        }
    }

    /**
     * Shikimori kayıtları için MAL ID'sini çözer (detay → ARM → Shikimori API zinciri).
     * Çözülemezse null döner — Shikimori ID'si ASLA MAL ID yerine kullanılmaz.
     */
    private fun resolveMalIdForShikimori(shikimoriId: Int, realMalId: Int?): Int? {
        realMalId?.takeIf { it > 0 }?.let { return it }
        DetailCache.getMediaDetail("shikimori", shikimoriId)?.realMalId?.takeIf { it > 0 }?.let { return it }
        return KitsugiIdResolver.resolveMalIdFromShikimori(shikimoriId)
    }

    /** Shikimori kaydı için AniList "stableId"si (100_000_000 + aniListId); yoksa null. */
    private suspend fun resolveAniListStableIdForShikimori(malId: Int?): Int? {
        val resolved = runCatching {
            KitsugiIdResolver.resolveIds(malId = malId, aniListId = null, tmdbId = null)
        }.getOrNull() ?: return null
        val aniListId = resolved.aniListId ?: return null
        return if (aniListId > 0) 100_000_000 + aniListId else null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun fetchRelationsFromJikan(
        externalId: Int,
        mediaType: MediaType
    ): List<KitsugiRelation> {
        val endpoint = MalJikanMediaSupport.jikanEndpoint(mediaType)
        val url = java.net.URL("https://api.jikan.moe/v4/$endpoint/$externalId/relations")
        return runCatching {
            KitsugiApiBase.runWithRateLimit {
                val response = KitsugiApiBase.executeGetRequestResilient(url) ?: return@runWithRateLimit emptyList()
                val root = JSONObject(response)
                val data = root.optJSONArray("data") ?: return@runWithRateLimit emptyList()
                val list = mutableListOf<KitsugiRelation>()

                for (i in 0 until data.length()) {
                    val item = data.optJSONObject(i) ?: continue
                    val relType = item.optNullableString("relation") ?: "Relation"
                    val entries = item.optJSONArray("entry") ?: continue
                    for (j in 0 until entries.length()) {
                        val entry = entries.optJSONObject(j) ?: continue
                        val relMalId = entry.optInt("mal_id")
                        val title = entry.optNullableString("name") ?: "Bilinmeyen"
                        val mTypeStr = entry.optNullableString("type").orEmpty()
                        val mType = if (mTypeStr.equals("manga", ignoreCase = true)) MediaType.Manga else MediaType.Anime
                        list.add(KitsugiRelation(
                            malId = relMalId,
                            title = title,
                            relationType = relType.toTurkishRelationType(),
                            imageUrl = null,
                            mediaType = mType,
                            source = "jikan"
                        ))
                    }
                }

                // Kapak görselleri hemen zenginleştirilir: Jikan relations görsel içermez
                // MAL/İlişkiler sekmesinde baş harf yerine kapak gösterilmesi için AniList kapakları eklenir.
                if (list.isNotEmpty() && list.all { it.imageUrl.isNullOrBlank() }) {
                    val enrichedOnce = runCatching { enrichJikanRelationsWithAniListCovers(list, mediaType) }.getOrNull()
                    if (enrichedOnce != null && enrichedOnce.any { !it.imageUrl.isNullOrBlank() }) {
                        return@runWithRateLimit enrichedOnce
                    }
                }
                list
            }
        }.getOrElse { emptyList() }
    }

    /**
     * Jikan ilişkilerindeki null kapakları AniList üzerinden topluca doldurur.
     * Toplu sorgu: `Page { media(idMal_in: [...]) { idMal coverImage { large } title { romaji english native } } }`
     * Frieren gibi popüler yapımlarda 5-15 ilişki için tek istek yeterlidir.
     */
    private suspend fun enrichJikanRelationsWithAniListCovers(
        relations: List<KitsugiRelation>,
        mediaType: MediaType
    ): List<KitsugiRelation> {
        if (relations.isEmpty()) return relations
        val malIds = relations.map { it.malId }.filter { it > 0 }.distinct().take(25)
        if (malIds.isEmpty()) return relations

        val coversByMalId = fetchAniListCoversByMalIds(malIds, mediaType)
        if (coversByMalId.isEmpty()) return relations

        // Başlık alternatifleri de doldurulursa detay sayfasında dil tercihleri doğru çalışır
        val titlesByMalId = fetchAniListTitlesByMalIds(malIds, mediaType)

        return relations.map { rel ->
            val cover = coversByMalId[rel.malId]
            val titles = titlesByMalId[rel.malId]
            if (cover != null) {
                rel.copy(
                    imageUrl = cover,
                    titleEnglish = titles?.first ?: rel.titleEnglish,
                    titleRomaji = titles?.second ?: rel.titleRomaji,
                    titleJapanese = titles?.third ?: rel.titleJapanese
                )
            } else rel
        }
    }

    private suspend fun fetchAniListCoversByMalIds(malIds: List<Int>, mediaType: MediaType): Map<Int, String> {
        if (malIds.isEmpty()) return emptyMap()
        val query = """
            query (${'$'}idsMal: [Int], ${'$'}type: MediaType) {
                Page(page: 1, perPage: 50) {
                    media(idMal_in: ${'$'}idsMal, type: ${'$'}type) {
                        idMal
                        coverImage { large }
                    }
                }
            }
        """.trimIndent()
        val variables = JSONObject().put("idsMal", org.json.JSONArray(malIds)).put("type", MalJikanMediaSupport.aniListMediaType(mediaType))
        return runCatching {
            val response = KitsugiApiBase.executeAniListQuery(query, variables) ?: return@runCatching emptyMap<Int, String>()
            val arr = JSONObject(response).optJSONObject("data")?.optJSONObject("Page")?.optJSONArray("media") ?: return@runCatching emptyMap<Int, String>()
            val map = mutableMapOf<Int, String>()
            for (i in 0 until arr.length()) {
                val node = arr.optJSONObject(i) ?: continue
                val idMal = node.optInt("idMal", 0)
                val cover = node.optJSONObject("coverImage")?.optNullableString("large")?.takeIf { it.isNotBlank() } ?: continue
                if (idMal > 0) map[idMal] = cover
            }
            map
        }.getOrElse { emptyMap() }
    }

    private suspend fun fetchAniListTitlesByMalIds(malIds: List<Int>, mediaType: MediaType): Map<Int, Triple<String?, String?, String?>> {
        if (malIds.isEmpty()) return emptyMap()
        val query = """
            query (${'$'}idsMal: [Int], ${'$'}type: MediaType) {
                Page(page: 1, perPage: 50) {
                    media(idMal_in: ${'$'}idsMal, type: ${'$'}type) {
                        idMal
                        title { romaji english native }
                    }
                }
            }
        """.trimIndent()
        val variables = JSONObject().put("idsMal", org.json.JSONArray(malIds)).put("type", MalJikanMediaSupport.aniListMediaType(mediaType))
        return runCatching {
            val response = KitsugiApiBase.executeAniListQuery(query, variables) ?: return@runCatching emptyMap<Int, Triple<String?, String?, String?>>()
            val arr = JSONObject(response).optJSONObject("data")?.optJSONObject("Page")?.optJSONArray("media") ?: return@runCatching emptyMap<Int, Triple<String?, String?, String?>>()
            val map = mutableMapOf<Int, Triple<String?, String?, String?>>()
            for (i in 0 until arr.length()) {
                val node = arr.optJSONObject(i) ?: continue
                val idMal = node.optInt("idMal", 0)
                if (idMal <= 0) continue
                val titleObj = node.optJSONObject("title")
                val romaji = titleObj?.optNullableString("romaji")?.takeIf { it.isNotBlank() }
                val english = titleObj?.optNullableString("english")?.takeIf { it.isNotBlank() }
                val native = titleObj?.optNullableString("native")?.takeIf { it.isNotBlank() }
                map[idMal] = Triple(english, romaji, native)
            }
            map
        }.getOrElse { emptyMap() }
    }

    private suspend fun fetchRelationsFromAniList(
        externalId: Int,
        mediaType: MediaType
    ): List<KitsugiRelation> {
        val aniListId = if (externalId >= 100_000_000) externalId - 100_000_000 else null
        val idParam  = if (aniListId != null) "\$id: Int" else "\$idMal: Int"
        val idFilter = if (aniListId != null) "id: \$id"  else "idMal: \$idMal"
        val query = """
            query (${'$'}type: MediaType, $idParam) {
                Media($idFilter, type: ${'$'}type) {
                    relations {
                        edges {
                            relationType(version: 2)
                            node {
                                id
                                idMal
                                title { romaji english native }
                                type
                                coverImage { large }
                                isAdult
                            }
                        }
                    }
                }
            }
        """.trimIndent()
        val variables = JSONObject().put("type", MalJikanMediaSupport.aniListMediaType(mediaType))
        if (aniListId != null) variables.put("id", aniListId) else variables.put("idMal", externalId)

        return runCatching {
            val response = KitsugiApiBase.executeAniListQuery(query, variables) ?: return emptyList()
            val root  = JSONObject(response)
            val edges = root.optJSONObject("data")
                ?.optJSONObject("Media")
                ?.optJSONObject("relations")
                ?.optJSONArray("edges") ?: return emptyList()
            val list = mutableListOf<KitsugiRelation>()
            for (i in 0 until edges.length()) {
                val edge = edges.optJSONObject(i) ?: continue
                val relType  = edge.optNullableString("relationType") ?: "Relation"
                val node     = edge.optJSONObject("node") ?: continue
                val nodeId   = node.optInt("id")
                val nodeIdMal = node.optionalPositiveInt("idMal")
                val stableId = nodeIdMal ?: (100_000_000 + nodeId)
                val titleObj = node.optJSONObject("title")
                val titleRomaji = titleObj?.optNullableString("romaji")
                val titleEnglish = titleObj?.optNullableString("english")
                val titleNative = titleObj?.optNullableString("native")
                val title = titleRomaji
                    ?: titleEnglish
                    ?: titleNative ?: "Bilinmeyen"
                val nodeTypeStr = node.optNullableString("type").orEmpty()
                val nodeType = if (nodeTypeStr.equals("manga", ignoreCase = true)) MediaType.Manga else MediaType.Anime
                val imageUrl = node.optJSONObject("coverImage")?.optNullableString("large")
                val isAdult = node.optBoolean("isAdult", false)
                list.add(KitsugiRelation(
                    malId = stableId,
                    title = title,
                    relationType = relType.toTurkishRelationType(),
                    imageUrl = imageUrl,
                    mediaType = nodeType,
                    source = "anilist",
                    titleEnglish = titleEnglish,
                    titleJapanese = titleNative,
                    titleRomaji = titleRomaji,
                    isAdult = isAdult
                ))
            }
            list
        }.getOrElse { emptyList() }
    }

    suspend fun fetchRecommendations(
        source: String,
        externalId: Int?,
        mediaType: MediaType,
        tmdbId: Int? = null,
        realMalId: Int? = null,
        title: String? = null
    ): List<KitsugiRelation> {
        return withContext(Dispatchers.IO) {
            if (source.equals("anilist", ignoreCase = true) && (externalId == null || externalId <= 0)) {
                // Kimliği bilinmeyen AniList kaydı: başlıkla ara (yanlış kimlikle veri getirme).
                val cleanTitle = title?.replace(Regex("\\s*\\(.*?\\)"), "")?.trim()
                if (cleanTitle.isNullOrBlank()) return@withContext emptyList()
                return@withContext fetchRecommendationsFromAniListBySearch(cleanTitle, mediaType)
            }
            if (externalId == null || externalId <= 0) return@withContext emptyList()

            when (MalJikanMediaSupport.canonicalSource(source)) {
                "bangumi" -> {
                    // 1) Bangumi'nin kendi önerileri (p1 /recs; sunucu en fazla 10 kayıt verir).
                    val native = runCatching { KitsugiBangumiDetailClient.fetchRecommendations(externalId, mediaType) }
                        .getOrNull().orEmpty()
                    if (native.size >= 4) return@withContext native

                    // 2) Az/boşsa MAL / AniList / TMDB önerileriyle tamamla (çözülen kimliklerle).
                    val cross = runCatching { KitsugiBangumiDetailClient.resolveCrossIds(externalId, mediaType) }.getOrNull()
                    val malId = cross?.malId ?: KitsugiBangumiDetailClient.sanitizeMalId(realMalId)
                    val extra: List<KitsugiRelation> = if (mediaType == MediaType.Anime || mediaType == MediaType.Manga) {
                        val fromMal = if (malId != null && malId > 0) fetchRecommendationsFromJikan(malId, mediaType) else emptyList()
                        if (fromMal.isNotEmpty()) {
                            fromMal
                        } else {
                            val aniListStable = cross?.aniListStableId ?: resolveAniListStableIdForShikimori(malId)
                            if (aniListStable != null) fetchRecommendationsFromAniList(aniListStable, mediaType) else emptyList()
                        }
                    } else {
                        val resolvedTmdb = tmdbId ?: cross?.tmdbId
                        if (resolvedTmdb != null && resolvedTmdb > 0) {
                            TmdbApiClient().fetchRecommendations(resolvedTmdb, mediaType == MediaType.Movie)
                                .map { it.copy(source = "tmdb") }
                        } else emptyList()
                    }
                    (native + extra).distinctBy { "${it.source}_${it.malId}" }
                }
                "simkl" -> {
                    val simklCross = KitsugiSimklDetailClient.resolveSimklCrossIds(
                        simklId = externalId,
                        mediaType = mediaType,
                        hintTmdbId = tmdbId,
                        hintMalId = realMalId
                    )
                    val detail = DetailCache.getMediaDetail("simkl", externalId)
                    val malId = realMalId?.takeIf { it > 0 && it != externalId }
                        ?: simklCross.malId
                        ?: detail?.realMalId
                    val resolvedTmdb = tmdbId?.takeIf { it > 0 }
                        ?: simklCross.tmdbId
                        ?: detail?.tmdbId
                    val aniListId = simklCross.aniListId
                    val effectiveTitle = title ?: simklCross.title ?: detail?.title ?: detail?.titleEnglish
                    val animeMediaType = if (mediaType == MediaType.Manga) MediaType.Manga else MediaType.Anime

                    val recs = mutableListOf<KitsugiRelation>()
                    if (mediaType == MediaType.Anime) {
                        if (malId != null && malId > 0) {
                            recs.addAll(
                                runCatching { fetchRecommendations("jikan", malId, animeMediaType, resolvedTmdb, malId, effectiveTitle) }
                                    .getOrNull().orEmpty()
                            )
                        } else if (aniListId != null && aniListId > 0) {
                            recs.addAll(
                                runCatching { fetchRecommendationsFromAniList(100_000_000 + aniListId, animeMediaType) }
                                    .getOrNull().orEmpty()
                            )
                        }
                    }

                    val simklNativeRecs = runCatching {
                        KitsugiSimklDetailClient.fetchSimklNativeRecommendations(externalId, mediaType)
                    }.getOrNull().orEmpty()
                    if (simklNativeRecs.isNotEmpty()) {
                        recs.addAll(simklNativeRecs)
                    }

                    if (resolvedTmdb != null && resolvedTmdb > 0) {
                        val tmdbRecommendations = runCatching {
                            TmdbApiClient().fetchRecommendations(resolvedTmdb, simklCross.isMovie)
                        }.getOrNull().orEmpty()
                        if (tmdbRecommendations.isNotEmpty()) {
                            recs.addAll(tmdbRecommendations.map { it.copy(source = "tmdb") })
                        }
                    }

                    recs.distinctBy { "${it.source}_${it.malId}" }
                }
                "tmdb" -> {
                    val effectiveTmdbId = tmdbId ?: externalId
                    if (effectiveTmdbId > 0) {
                        val isMovie = mediaType == MediaType.Movie
                        val mediaDetail = DetailCache.getMediaDetail("tmdb", effectiveTmdbId)
                        val malId = realMalId ?: mediaDetail?.realMalId
                        if (malId != null && malId > 0) {
                            val malList = fetchRecommendations("jikan", malId, mediaType, null, null, title)
                            if (malList.isNotEmpty()) return@withContext malList
                        }

                        val cleanTitle = (title ?: mediaDetail?.title ?: mediaDetail?.titleEnglish)
                            ?.replace(Regex("\\s*\\(.*?\\)"), "")?.trim()
                        if (!cleanTitle.isNullOrBlank()) {
                            val aniRecs = fetchRecommendationsFromAniListBySearch(cleanTitle, mediaType)
                            if (aniRecs.isNotEmpty()) return@withContext aniRecs
                        }

                        TmdbApiClient().fetchRecommendations(effectiveTmdbId, isMovie).map { it.copy(source = "tmdb") }
                    } else emptyList()
                }
                "jikan", "mal" -> {
                    val malId = MalJikanMediaSupport.resolveMalId(source, externalId, realMalId)
                        ?: return@withContext emptyList()
                    val jikanRecommendations = fetchRecommendationsFromJikan(malId, mediaType)
                    if (jikanRecommendations.isNotEmpty()) return@withContext jikanRecommendations

                    // Exact MAL-ID fallback keeps both MAL/Jikan result types on the same path.
                    val aniListRecommendations = fetchRecommendationsFromAniList(malId, mediaType)
                    if (aniListRecommendations.isNotEmpty()) return@withContext aniListRecommendations

                    // TMDB is a last-resort source. Do not resolve IDs through ARM or wait for
                    // TMDB when Jikan already has recommendations to show.
                    if (mediaType != MediaType.Manga && tmdbId != null && tmdbId > 0) {
                        TmdbApiClient().fetchRecommendations(tmdbId, mediaType == MediaType.Movie)
                            .map { it.copy(source = "tmdb") }
                    } else {
                        emptyList()
                    }
                }
                "kitsu" -> {
                    val kitsuOffset = 300_000_000
                    val kitsuNumericId = if (externalId >= kitsuOffset) externalId - kitsuOffset else externalId
                    if (kitsuNumericId <= 0) return@withContext emptyList()

                    val resolved = runCatching {
                        KitsugiIdResolver.resolveIds(malId = realMalId, aniListId = null, kitsuId = kitsuNumericId)
                    }.getOrNull()

                    val aniListId = resolved?.aniListId
                    if (aniListId != null && aniListId > 0) {
                        val encoded = 100_000_000 + aniListId
                        val list = fetchRecommendationsFromAniList(encoded, mediaType)
                        if (list.isNotEmpty()) return@withContext list
                    }

                    val jikanId = realMalId?.takeIf { it > 0 } ?: resolved?.malId?.takeIf { it > 0 }
                    if (jikanId != null) {
                        val list = fetchRecommendationsFromJikan(jikanId, mediaType)
                        if (list.isNotEmpty()) return@withContext list
                    }

                    emptyList()
                }
                "shikimori" -> {
                    // 1) Shikimori'nin KENDİ `similar` ucu: MAL/ARM eşlemesi gerektirmez.
                    val native = runCatching {
                        KitsugiShikimoriClient.fetchSimilarRecommendations(externalId, mediaType)
                    }.getOrNull().orEmpty()
                    if (native.isNotEmpty()) return@withContext native

                    // 2) Yedek: gerçek MAL ID'si çözülür (Shikimori ID'si MAL ID değildir).
                    val malId = resolveMalIdForShikimori(externalId, realMalId)
                    if (malId != null && malId > 0) {
                        val list = fetchRecommendationsFromJikan(
                            malId,
                            if (mediaType == MediaType.Manga) MediaType.Manga else MediaType.Anime
                        )
                        if (list.isNotEmpty()) return@withContext list
                    }
                    val aniListStableId = resolveAniListStableIdForShikimori(malId)
                    if (aniListStableId != null) fetchRecommendationsFromAniList(aniListStableId, mediaType) else emptyList()
                }
                "anilist"      -> fetchRecommendationsFromAniList(externalId, mediaType)
                else           -> emptyList()
            }
        }
    }

    private suspend fun fetchRecommendationsFromJikan(
        externalId: Int,
        mediaType: MediaType
    ): List<KitsugiRelation> {
        val endpoint = MalJikanMediaSupport.jikanEndpoint(mediaType)
        val url = java.net.URL("https://api.jikan.moe/v4/$endpoint/$externalId/recommendations")
        return runCatching {
            KitsugiApiBase.runWithRateLimit {
                val response = KitsugiApiBase.executeGetRequestResilient(url) ?: return@runWithRateLimit emptyList()
                val root = JSONObject(response)
                val data = root.optJSONArray("data") ?: return@runWithRateLimit emptyList()
                val list = mutableListOf<KitsugiRelation>()

                for (i in 0 until minOf(data.length(), 20)) {
                    val item = data.optJSONObject(i) ?: continue
                    val entry = item.optJSONObject("entry") ?: continue
                    val relMalId = entry.optInt("mal_id")
                    val title = entry.optNullableString("title") ?: "Bilinmeyen"
                    val imagesObj = entry.optJSONObject("images")
                    val imageUrl = imagesObj?.optJSONObject("jpg")?.optNullableString("image_url")
                        ?: imagesObj?.optJSONObject("webp")?.optNullableString("image_url")
                    
                    list.add(KitsugiRelation(
                        malId = relMalId,
                        title = title,
                        relationType = KitsugiStrings.get(R.string.oneri),
                        imageUrl = imageUrl,
                        mediaType = mediaType,
                        source = "jikan"
                    ))
                }

                // Öneri kartlarının Jikan'dan gelen başlık ve kapakları hemen gösterilir;
                // ek bir AniList isteği temel sonucun önüne geçmez.
                list
            }
        }.getOrElse { emptyList() }
    }

    private suspend fun fetchRecommendationsFromAniList(
        externalId: Int,
        mediaType: MediaType
    ): List<KitsugiRelation> {
        val aniListId = if (externalId >= 100_000_000) externalId - 100_000_000 else null
        val idParam  = if (aniListId != null) "\$id: Int" else "\$idMal: Int"
        val idFilter = if (aniListId != null) "id: \$id"  else "idMal: \$idMal"
        val query = """
            query (${'$'}type: MediaType, $idParam) {
                Media($idFilter, type: ${'$'}type) {
                    recommendations(page: 1, perPage: 20, sort: [RATING_DESC, ID]) {
                        edges {
                            node {
                                mediaRecommendation {
                                    id
                                    idMal
                                    title { romaji english native }
                                    type
                                    coverImage { large }
                                    isAdult
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent()
        val variables = JSONObject().put("type", MalJikanMediaSupport.aniListMediaType(mediaType))
        if (aniListId != null) variables.put("id", aniListId) else variables.put("idMal", externalId)

        return runCatching {
            val response = KitsugiApiBase.executeAniListQuery(query, variables) ?: return emptyList()
            val root  = JSONObject(response)
            val edges = root.optJSONObject("data")
                ?.optJSONObject("Media")
                ?.optJSONObject("recommendations")
                ?.optJSONArray("edges") ?: return emptyList()
            val list = mutableListOf<KitsugiRelation>()
            for (i in 0 until edges.length()) {
                val edge = edges.optJSONObject(i) ?: continue
                val node = edge.optJSONObject("node") ?: continue
                val mediaRec = node.optJSONObject("mediaRecommendation") ?: continue
                val nodeId   = mediaRec.optInt("id")
                val nodeIdMal = mediaRec.optionalPositiveInt("idMal")
                val stableId = nodeIdMal ?: (100_000_000 + nodeId)
                val titleObj = mediaRec.optJSONObject("title")
                val titleRomaji = titleObj?.optNullableString("romaji")
                val titleEnglish = titleObj?.optNullableString("english")
                val titleNative = titleObj?.optNullableString("native")
                val title = titleRomaji
                    ?: titleEnglish
                    ?: titleNative ?: "Bilinmeyen"
                val nodeTypeStr = mediaRec.optNullableString("type").orEmpty()
                val nodeType = if (nodeTypeStr.equals("manga", ignoreCase = true)) MediaType.Manga else MediaType.Anime
                val imageUrl = mediaRec.optJSONObject("coverImage")?.optNullableString("large")
                val isAdult = mediaRec.optBoolean("isAdult", false)
                list.add(KitsugiRelation(
                    malId = stableId,
                    title = title,
                    relationType = KitsugiStrings.get(R.string.oneri),
                    imageUrl = imageUrl,
                    mediaType = nodeType,
                    source = "anilist",
                    titleEnglish = titleEnglish,
                    titleJapanese = titleNative,
                    titleRomaji = titleRomaji,
                    isAdult = isAdult
                ))
            }
            list
        }.getOrElse { emptyList() }
    }

    private suspend fun fetchRelationsFromAniListBySearch(
        searchTitle: String,
        mediaType: MediaType
    ): List<KitsugiRelation> {
        val query = """
            query (${'$'}search: String, ${'$'}type: MediaType) {
                Media(search: ${'$'}search, type: ${'$'}type) {
                    relations {
                        edges {
                            relationType(version: 2)
                            node {
                                id
                                idMal
                                title { romaji english native }
                                type
                                coverImage { large }
                                isAdult
                            }
                        }
                    }
                }
            }
        """.trimIndent()
        val variables = JSONObject()
            .put("search", searchTitle)
            .put("type", MalJikanMediaSupport.aniListMediaType(mediaType))

        return runCatching {
            val response = KitsugiApiBase.executeAniListQuery(query, variables) ?: return emptyList()
            val root = JSONObject(response)
            val edges = root.optJSONObject("data")
                ?.optJSONObject("Media")
                ?.optJSONObject("relations")
                ?.optJSONArray("edges") ?: return emptyList()
            val list = mutableListOf<KitsugiRelation>()
            for (i in 0 until edges.length()) {
                val edge = edges.optJSONObject(i) ?: continue
                val relType = edge.optNullableString("relationType") ?: "Relation"
                val node = edge.optJSONObject("node") ?: continue
                val nodeId = node.optInt("id")
                val nodeIdMal = node.optionalPositiveInt("idMal")
                val stableId = nodeIdMal ?: (100_000_000 + nodeId)
                val titleObj = node.optJSONObject("title")
                val titleRomaji = titleObj?.optNullableString("romaji")
                val titleEnglish = titleObj?.optNullableString("english")
                val titleNative = titleObj?.optNullableString("native")
                val title = titleRomaji ?: titleEnglish ?: titleNative ?: "Bilinmeyen"
                val nodeTypeStr = node.optNullableString("type").orEmpty()
                val nodeType = if (nodeTypeStr.equals("manga", ignoreCase = true)) MediaType.Manga else MediaType.Anime
                val imageUrl = node.optJSONObject("coverImage")?.optNullableString("large")
                val isAdult = node.optBoolean("isAdult", false)
                list.add(
                    KitsugiRelation(
                        malId = stableId,
                        title = title,
                        relationType = relType.toTurkishRelationType(),
                        imageUrl = imageUrl,
                        mediaType = nodeType,
                        source = "anilist",
                        titleEnglish = titleEnglish,
                        titleJapanese = titleNative,
                        titleRomaji = titleRomaji,
                        isAdult = isAdult
                    )
                )
            }
            list
        }.getOrElse { emptyList() }
    }

    private suspend fun fetchRecommendationsFromAniListBySearch(
        searchTitle: String,
        mediaType: MediaType
    ): List<KitsugiRelation> {
        val query = """
            query (${'$'}search: String, ${'$'}type: MediaType) {
                Media(search: ${'$'}search, type: ${'$'}type) {
                    recommendations(page: 1, perPage: 20, sort: [RATING_DESC, ID]) {
                        edges {
                            node {
                                mediaRecommendation {
                                    id
                                    idMal
                                    title { romaji english native }
                                    type
                                    coverImage { large }
                                    isAdult
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent()
        val variables = JSONObject()
            .put("search", searchTitle)
            .put("type", MalJikanMediaSupport.aniListMediaType(mediaType))

        return runCatching {
            val response = KitsugiApiBase.executeAniListQuery(query, variables) ?: return emptyList()
            val root = JSONObject(response)
            val edges = root.optJSONObject("data")
                ?.optJSONObject("Media")
                ?.optJSONObject("recommendations")
                ?.optJSONArray("edges") ?: return emptyList()
            val list = mutableListOf<KitsugiRelation>()
            for (i in 0 until edges.length()) {
                val edge = edges.optJSONObject(i) ?: continue
                val node = edge.optJSONObject("node") ?: continue
                val mediaRec = node.optJSONObject("mediaRecommendation") ?: continue
                val nodeId = mediaRec.optInt("id")
                val nodeIdMal = mediaRec.optionalPositiveInt("idMal")
                val stableId = nodeIdMal ?: (100_000_000 + nodeId)
                val titleObj = mediaRec.optJSONObject("title")
                val titleRomaji = titleObj?.optNullableString("romaji")
                val titleEnglish = titleObj?.optNullableString("english")
                val titleNative = titleObj?.optNullableString("native")
                val title = titleRomaji ?: titleEnglish ?: titleNative ?: "Bilinmeyen"
                val nodeTypeStr = mediaRec.optNullableString("type").orEmpty()
                val nodeType = if (nodeTypeStr.equals("manga", ignoreCase = true)) MediaType.Manga else MediaType.Anime
                val imageUrl = mediaRec.optJSONObject("coverImage")?.optNullableString("large")
                val isAdult = mediaRec.optBoolean("isAdult", false)
                list.add(
                    KitsugiRelation(
                        malId = stableId,
                        title = title,
                        relationType = KitsugiStrings.get(R.string.oneri),
                        imageUrl = imageUrl,
                        mediaType = nodeType,
                        source = "anilist",
                        titleEnglish = titleEnglish,
                        titleJapanese = titleNative,
                        titleRomaji = titleRomaji,
                        isAdult = isAdult
                    )
                )
            }
            list
        }.getOrElse { emptyList() }
    }
}
