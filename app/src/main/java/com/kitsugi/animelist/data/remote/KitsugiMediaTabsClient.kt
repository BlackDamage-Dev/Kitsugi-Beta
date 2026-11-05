package com.kitsugi.animelist.data.remote

import android.content.Context
import android.util.Log
import com.kitsugi.animelist.data.local.KitsugiDatabase
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.data.settings.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.R

/**
 * Bölüm listesi çekme işlevlerini barındıran istemci.
 * Diğer metotlar (social, relations, mutations client'larına) bölünmüştür.
 */
class KitsugiMediaTabsClient {

    private suspend fun isTmdbEpisodeFallbackEnabled(context: Context?): Boolean {
        val appContext = context?.applicationContext
            ?: com.kitsugi.animelist.KitsugiApplication.getInstance()?.applicationContext
            ?: return true
        return runCatching {
            val settings = SettingsDataStore(appContext).settingsFlow.first()
            settings.tmdbEnabled && settings.tmdbUseEpisodes
        }.getOrDefault(true)
    }

    suspend fun fetchEpisodes(
        source: String,
        externalId: Int?,
        mediaType: MediaType,
        realMalId: Int? = null,
        totalEpisodes: Int? = null,
        context: Context? = null,
        targetSeason: Int? = null,
        tmdbId: Int? = null
    ): List<KitsugiStreamingEpisode> {
        return withContext(Dispatchers.IO) {
            if (externalId == null || externalId <= 0) return@withContext emptyList()
            if (mediaType == MediaType.Movie) return@withContext emptyList()

            val effectiveSource = MalJikanMediaSupport.canonicalSource(source)
            val effectiveId = externalId

            // Tüm 7 kaynak tek ortak akışı kullanır: native liste → TMDB takviyesi → dolgu.
            // Jikan/MAL/AniList eskiden buraya ayrı bir erken dönüşleydi; artık aşağıdaki
            // ortak `when` ve TMDB birleştirmesinden geçer (görsel/tamlık tutarlılığı).

            // Shikimori: kimlik uzayı MAL'dan farklı olabilir. Bölüm listesi ve TMDB
            // eşlemesi için gerçek MAL ID'si BİR KEZ çözülür ve iki adımda da kullanılır.
            val shikimoriMalId = if (effectiveSource == "shikimori") {
                realMalId?.takeIf { it > 0 }
                    ?: DetailCache.getMediaDetail("shikimori", effectiveId)?.realMalId?.takeIf { it > 0 }
                    ?: KitsugiIdResolver.resolveMalIdFromShikimori(effectiveId)
            } else null

            // Bangumi: stableId (500M+) MAL ID DEĞİLDİR. Bölümler Bangumi'nin kendi ucundan gelir;
            // TMDB bölüm adı/görsel eşlemesi ve MAL yedeği çözülen çapraz kimliklerle yapılır.
            val bangumiCross = if (effectiveSource == "bangumi") {
                runCatching { KitsugiBangumiDetailClient.resolveCrossIds(effectiveId, mediaType) }.getOrNull()
            } else null
            val bangumiMalId = if (effectiveSource == "bangumi") {
                bangumiCross?.malId ?: KitsugiBangumiDetailClient.sanitizeMalId(realMalId)
            } else null

            val list: List<KitsugiStreamingEpisode> = when (effectiveSource) {
                "jikan", "mal" -> {
                    val malId = MalJikanMediaSupport.resolveMalId(effectiveSource, effectiveId, realMalId)
                        ?: return@withContext emptyList()
                    fetchEpisodesFromJikan(malId)
                }
                "anilist" -> {
                    // AniList'in streamingEpisodes yalnızca birkaç streaming bağlantısı listeler,
                    // tam bölüm listesini değil. Gerçek MAL id varsa tam liste Jikan'den çekilir;
                    // yoksa AniList streamingEpisodes'ına düşülür.
                    val malId = MalJikanMediaSupport.resolveMalId(effectiveSource, effectiveId, realMalId)
                    if (malId != null && malId > 0) fetchEpisodesFromJikan(malId)
                    else fetchEpisodesFromAniList(effectiveId)
                }
                "bangumi" -> {
                    val native = runCatching { KitsugiBangumiDetailClient.fetchEpisodes(effectiveId) }
                        .getOrNull().orEmpty()
                    if (native.isNotEmpty()) {
                        native
                    } else if (bangumiMalId != null && bangumiMalId > 0 && mediaType == MediaType.Anime) {
                        fetchEpisodesFromJikan(bangumiMalId)
                    } else emptyList()
                }
                "kitsu" -> {
                    // externalId = kitsuStableId = kitsuNumericId + 300_000_000
                    val kitsuOffset = 300_000_000
                    val kitsuNumericId = if (effectiveId >= kitsuOffset) effectiveId - kitsuOffset else effectiveId
                    Log.d("KitsugiMediaTabsClient", "Kitsu episode fetch: kitsuNumericId=$kitsuNumericId")
                    if (kitsuNumericId > 0) {
                        runCatching { KitsuClient.fetchKitsuEpisodes(kitsuNumericId) }.getOrElse { emptyList() }
                    } else emptyList()
                }
                "simkl" -> {
                    // Simkl için önce realMalId varsa Jikan'dan çek (anime)
                    if (realMalId != null && realMalId > 0) fetchEpisodesFromJikan(realMalId) else emptyList()
                }
                "shikimori" -> {
                    // Shikimori ID'si MAL ID'si DEĞİLDİR: bölüm listesi için gerçek MAL ID'si
                    // kullanılır (yukarıda çözülür). Eşleme yoksa liste boş kalır — aksi hâlde
                    // Shikimori ID'si MAL sanılıp ALAKASIZ bir yapımın bölümleri gösterilirdi.
                    if (shikimoriMalId != null && shikimoriMalId > 0) {
                        fetchEpisodesFromJikan(shikimoriMalId)
                    } else emptyList()
                }
                else -> emptyList()
            }

            var finalTotalEpisodes = totalEpisodes
            if (finalTotalEpisodes == null && context != null) {
                try {
                    val db = KitsugiDatabase.getDatabase(context.applicationContext)
                    val entry = db.mediaEntryDao().getByMalId(externalId)
                        ?: if (realMalId != null) db.mediaEntryDao().getByMalId(realMalId) else null
                    if (entry != null) {
                        finalTotalEpisodes = entry.total
                    }
                } catch (e: Exception) {
                    Log.e("KitsugiMediaTabsClient", "Error querying total episodes from DB", e)
                }
            }

            // TMDB kendisi bir kaynaktır: bölümleri her zaman çekilir. Diğer sağlayıcılarda
            // TMDB başlıkları/görselleri kullanıcı ayarına bağlı takviyedir.
            if (effectiveSource == "tmdb" ||
                ((mediaType == MediaType.Anime || mediaType == MediaType.TvShow) && isTmdbEpisodeFallbackEnabled(context))
            ) {
                // TMDB-derived titles/stills are optional; preserve native source episode lists when disabled.
                var tmdbEpisodes: List<KitsugiEpisodeRatingsRepository.TmdbEpisodeDto> = emptyList()
                try {
                    // NOT: Shikimori/Kitsu gibi kaynakların kimlikleri MAL ID'si değildir.
                    // Bu yüzden TMDB çözümlemesi bu kaynaklarda yalnızca gerçek MAL ID'si
                    // (realMalId) üzerinden yapılır; eskiden Shikimori ID'si MAL sanılıp
                    // alakasız yapımların bölüm adları/görselleri gösteriliyordu.
                    val tmdbIdVal = when (effectiveSource) {
                        "simkl" -> {
                            if (tmdbId != null && tmdbId > 0) {
                                tmdbId
                            } else if (realMalId != null && realMalId > 0) {
                                KitsugiEpisodeRatingsRepository.getResolvedTmdbIdForMal(realMalId)
                            } else DetailCache.getMediaDetail("simkl", effectiveId)?.tmdbId?.takeIf { it > 0 }
                        }
                        "tmdb" -> effectiveId
                        "bangumi" -> {
                            if (tmdbId != null && tmdbId > 0) {
                                tmdbId
                            } else if (bangumiCross?.tmdbId != null && bangumiCross.tmdbId > 0) {
                                bangumiCross.tmdbId
                            } else if (bangumiMalId != null && bangumiMalId > 0) {
                                KitsugiEpisodeRatingsRepository.getResolvedTmdbIdForMal(bangumiMalId)
                            } else null
                        }
                        "shikimori" -> {
                            if (tmdbId != null && tmdbId > 0) {
                                tmdbId
                            } else if (shikimoriMalId != null && shikimoriMalId > 0) {
                                KitsugiEpisodeRatingsRepository.getResolvedTmdbIdForMal(shikimoriMalId)
                            } else null
                        }
                        "kitsu" -> {
                            if (tmdbId != null && tmdbId > 0) {
                                tmdbId
                            } else if (realMalId != null && realMalId > 0) {
                                KitsugiEpisodeRatingsRepository.getResolvedTmdbIdForMal(realMalId)
                            } else null
                        }
                        else -> KitsugiEpisodeRatingsRepository.getResolvedTmdbIdForMal(effectiveId)
                    }

                    val resolvedId = tmdbIdVal ?: run {
                        when (effectiveSource) {
                            "simkl" -> {
                                if (tmdbId != null && tmdbId > 0) {
                                    tmdbId
                                } else if (realMalId != null && realMalId > 0) {
                                    KitsugiEpisodeRatingsRepository.resolveTmdbIdFromMal(realMalId)
                                } else DetailCache.getMediaDetail("simkl", effectiveId)?.tmdbId?.takeIf { it > 0 }
                            }
                            "tmdb" -> effectiveId
                            "bangumi" -> {
                                if (tmdbId != null && tmdbId > 0) {
                                    tmdbId
                                } else if (bangumiCross?.tmdbId != null && bangumiCross.tmdbId > 0) {
                                    bangumiCross.tmdbId
                                } else if (bangumiMalId != null && bangumiMalId > 0) {
                                    KitsugiEpisodeRatingsRepository.resolveTmdbIdFromMal(bangumiMalId)
                                } else null
                            }
                            "shikimori" -> {
                                if (tmdbId != null && tmdbId > 0) {
                                    tmdbId
                                } else if (shikimoriMalId != null && shikimoriMalId > 0) {
                                    KitsugiEpisodeRatingsRepository.resolveTmdbIdFromMal(shikimoriMalId)
                                } else null
                            }
                            "kitsu" -> {
                                if (tmdbId != null && tmdbId > 0) {
                                    tmdbId
                                } else if (realMalId != null && realMalId > 0) {
                                    KitsugiEpisodeRatingsRepository.resolveTmdbIdFromMal(realMalId)
                                } else null
                            }
                            else -> KitsugiEpisodeRatingsRepository.resolveTmdbIdFromMal(effectiveId)
                        }
                    }

                    if (resolvedId != null && resolvedId > 0) {
                        val sNum = targetSeason ?: 1
                        tmdbEpisodes = KitsugiEpisodeRatingsRepository.getTmdbEpisodes(resolvedId, sNum)
                        Log.d("KitsugiMediaTabsClient", "Fetched ${tmdbEpisodes.size} episodes from TMDB for tmdbId=$resolvedId, season=$sNum")
                    }
                } catch (e: Exception) {
                    Log.e("KitsugiMediaTabsClient", "Error resolving TMDB ID or fetching TMDB episodes", e)
                }

                // Filter out future (not yet aired) episodes from TMDB episodes
                val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                val filteredTmdbEpisodes = tmdbEpisodes.filter { ep ->
                    val date = ep.airDate
                    date.isNullOrBlank() || date <= todayStr
                }

                val maxFetchedEp = maxOf(
                    list.maxOfOrNull { it.episodeNumber ?: 0 } ?: 0,
                    filteredTmdbEpisodes.maxOfOrNull { it.episodeNumber } ?: 0
                )

                // Check if the media is currently airing
                val cachedDetail = DetailCache.getMediaDetail(source, effectiveId)
                    ?: if (realMalId != null) DetailCache.getMediaDetail("mal", realMalId) else null
                    ?: if (realMalId != null) DetailCache.getMediaDetail("anilist", realMalId) else null
                val isAiring = cachedDetail?.status?.let { s ->
                    s.contains("airing", ignoreCase = true) ||
                    s.contains("releasing", ignoreCase = true) ||
                    s.contains("yayında", ignoreCase = true) ||
                    s.contains("devam eden", ignoreCase = true) ||
                    s.contains("yapımda", ignoreCase = true) ||
                    s.contains("yayımlanıyor", ignoreCase = true)
                } == true

                val limit = if (filteredTmdbEpisodes.isNotEmpty() || (isAiring && maxFetchedEp > 0)) {
                    // TMDB season episodes are fetched or currently airing:
                    // They define the season's episode count. We don't pad to the entire show's total episodes.
                    maxFetchedEp
                } else {
                    // Fallback to Jikan/AniList list and/or total episodes
                    maxOf(finalTotalEpisodes ?: 0, maxFetchedEp)
                }
                val finalLimit = if (limit > 0) limit else 12

                val existingMap = list.associateBy { it.episodeNumber ?: -1 }
                val tmdbMap = filteredTmdbEpisodes.associateBy { it.episodeNumber }
                val resultList = mutableListOf<KitsugiStreamingEpisode>()

                for (epNum in 1..finalLimit) {
                    val existing = existingMap[epNum]
                    val tmdbEp = tmdbMap[epNum]

                    val tmdbThumb = tmdbEp?.stillPath?.let { "https://image.tmdb.org/t/p/w300$it" }
                    val tmdbCleanName = tmdbEp?.name?.trim()?.takeIf { name ->
                        name.isNotBlank() &&
                        !name.equals("Episode $epNum", ignoreCase = true) &&
                        !name.equals("Bölüm $epNum", ignoreCase = true) &&
                        !name.matches(Regex("""^\d+(\.\s*)?(Bölüm|Episode)?$""", RegexOption.IGNORE_CASE))
                    }

                    val finalTitle = when {
                        existing != null -> {
                            val isPlaceholder = existing.title.matches(Regex("""^(#\d+\s*–\s*)?(Bölüm|Episode)\s*\d+$""", RegexOption.IGNORE_CASE)) ||
                                                existing.title.matches(Regex("""^(Bölüm|Episode)\s*\d+$""", RegexOption.IGNORE_CASE))
                            if (isPlaceholder && tmdbCleanName != null) {
                                "#$epNum – $tmdbCleanName"
                            } else {
                                existing.title
                            }
                        }
                        tmdbCleanName != null -> {
                            "#$epNum – $tmdbCleanName"
                        }
                        else -> {
                            "Bölüm $epNum"
                        }
                    }

                    if (existing != null) {
                        resultList.add(
                            existing.copy(
                                title = finalTitle,
                                thumbnail = existing.thumbnail ?: tmdbThumb,
                                seasonNumber = targetSeason ?: existing.seasonNumber ?: 1
                            )
                        )
                    } else {
                        resultList.add(
                            KitsugiStreamingEpisode(
                                title = finalTitle,
                                thumbnail = tmdbThumb,
                                url = null,
                                site = "TMDB",
                                seasonNumber = targetSeason ?: 1,
                                episodeNumber = epNum
                            )
                        )
                    }
                }
                applyLocalCustomThumbnails(context, effectiveId, realMalId, resultList)
            } else {
                // TMDB kapalı: özgün liste ek olarak doldurularak anime/TV girişleri boş kalmaz.
                val siteLabel = when (effectiveSource) {
                    "anilist" -> "AniList"
                    "kitsu" -> "Kitsu"
                    "bangumi" -> "Bangumi"
                    "shikimori" -> "Shikimori"
                    "simkl" -> "Simkl"
                    "tmdb" -> "TMDB"
                    else -> "MyAnimeList"
                }
                val cachedDetail = DetailCache.getMediaDetail(source, effectiveId)
                    ?: if (realMalId != null) DetailCache.getMediaDetail("mal", realMalId) else null
                    ?: if (realMalId != null) DetailCache.getMediaDetail("anilist", realMalId) else null
                val isAiring = cachedDetail?.status?.let { s ->
                    s.contains("airing", ignoreCase = true) ||
                    s.contains("releasing", ignoreCase = true) ||
                    s.contains("yayında", ignoreCase = true) ||
                    s.contains("devam eden", ignoreCase = true) ||
                    s.contains("yapımda", ignoreCase = true) ||
                    s.contains("yayımlanıyor", ignoreCase = true)
                } == true
                val maxFetchedEp = list.maxOfOrNull { it.episodeNumber ?: 0 } ?: 0
                val limit = if (isAiring && maxFetchedEp > 0) {
                    maxFetchedEp
                } else {
                    maxOf(finalTotalEpisodes ?: 0, maxFetchedEp)
                }
                val finalLimit = if (limit > 0) limit else 12
                if (list.size < finalLimit) {
                    val existingMap = list.associateBy { it.episodeNumber ?: -1 }
                    val paddedList = mutableListOf<KitsugiStreamingEpisode>()
                    for (epNum in 1..finalLimit) {
                        val existing = existingMap[epNum]
                        if (existing != null) {
                            paddedList.add(existing)
                        } else {
                            paddedList.add(
                                KitsugiStreamingEpisode(
                                    title = KitsugiStrings.get(R.string.inline_episode_1_s_dc662be, epNum),
                                    thumbnail = null,
                                    url = null,
                                    site = siteLabel,
                                    seasonNumber = targetSeason ?: 1,
                                    episodeNumber = epNum
                                )
                            )
                        }
                    }
                    applyLocalCustomThumbnails(context, effectiveId, realMalId, paddedList)
                } else {
                    applyLocalCustomThumbnails(context, effectiveId, realMalId, list)
                }
            }
        }
    }

    private suspend fun applyLocalCustomThumbnails(
        context: Context?,
        externalId: Int,
        realMalId: Int?,
        episodes: List<KitsugiStreamingEpisode>
    ): List<KitsugiStreamingEpisode> {
        if (context == null || episodes.isEmpty()) return episodes
        val thumbDir = java.io.File(context.applicationContext.filesDir, "thumbnails")
        if (!thumbDir.exists()) return episodes

        val candidatePrefixes = linkedSetOf<String>()
        candidatePrefixes.add(externalId.toString())
        if (realMalId != null && realMalId > 0) {
            candidatePrefixes.add(realMalId.toString())
            candidatePrefixes.add("mal_$realMalId")
        }
        runCatching {
            val db = KitsugiDatabase.getDatabase(context.applicationContext)
            val entry = db.mediaEntryDao().getByMalId(externalId)
                ?: (if (realMalId != null) db.mediaEntryDao().getByMalId(realMalId) else null)
                ?: db.mediaEntryDao().getById(externalId)
            if (entry != null) {
                candidatePrefixes.add(entry.id.toString())
            }
        }

        return episodes.map { ep ->
            val epNum = ep.episodeNumber ?: return@map ep
            val localFile = candidatePrefixes.firstNotNullOfOrNull { prefix ->
                java.io.File(thumbDir, "${prefix}_${epNum}.png").takeIf { it.exists() && it.length() > 0 }
            }
            if (localFile != null) {
                ep.copy(thumbnail = "file://${localFile.absolutePath}")
            } else {
                ep
            }
        }
    }

    private suspend fun fetchEpisodesFromJikan(malId: Int): List<KitsugiStreamingEpisode> {
        val list = mutableListOf<KitsugiStreamingEpisode>()
        for (page in 1..3) {
            val url = URL("https://api.jikan.moe/v4/anime/$malId/episodes?page=$page")
            val pageList = runCatching {
                KitsugiApiBase.runWithRateLimit {
                    val response = KitsugiApiBase.executeGetRequestResilient(url) ?: return@runWithRateLimit emptyList<KitsugiStreamingEpisode>()
                    val root = JSONObject(response)
                    val data = root.optJSONArray("data") ?: return@runWithRateLimit emptyList<KitsugiStreamingEpisode>()
                    val pageItems = mutableListOf<KitsugiStreamingEpisode>()
                    for (i in 0 until data.length()) {
                        val ep = data.optJSONObject(i) ?: continue
                        val epNum = ep.optInt("mal_id", (page - 1) * 100 + i + 1)
                        val titleRomaji = ep.optNullableString("title")
                        val titleEn = ep.optNullableString("title_english")
                        val epTitle = titleRomaji ?: titleEn ?: "Bölüm $epNum"
                        pageItems.add(
                            KitsugiStreamingEpisode(
                                title = "#$epNum – $epTitle",
                                thumbnail = null,
                                url = ep.optNullableString("url"),
                                site = "MyAnimeList",
                                seasonNumber = 1,
                                episodeNumber = epNum
                            )
                        )
                    }
                    pageItems
                }
            }.getOrElse { emptyList() }

            list.addAll(pageList)
            if (pageList.size < 100) break
        }
        return list.sortedBy { it.episodeNumber ?: 0 }
    }

    private suspend fun fetchEpisodesFromAniList(externalId: Int): List<KitsugiStreamingEpisode> {
        val aniListId = if (externalId >= 100_000_000) externalId - 100_000_000 else null
        val idParam = if (aniListId != null) "\$id: Int" else "\$idMal: Int"
        val idFilter = if (aniListId != null) "id: \$id" else "idMal: \$idMal"
        val query = """
            query (${'$'}type: MediaType, $idParam) {
                Media($idFilter, type: ${'$'}type) {
                    streamingEpisodes { title thumbnail url site }
                }
            }
        """.trimIndent()
        val variables = JSONObject().put("type", "ANIME")
        if (aniListId != null) variables.put("id", aniListId) else variables.put("idMal", externalId)

        return runCatching {
            val response = KitsugiApiBase.executeAniListQuery(query, variables) ?: return emptyList()
            val root = JSONObject(response)
            val eps = root.optJSONObject("data")?.optJSONObject("Media")?.optJSONArray("streamingEpisodes")
                ?: return emptyList()
            val list = mutableListOf<KitsugiStreamingEpisode>()
            for (i in 0 until eps.length()) {
                val ep = eps.optJSONObject(i) ?: continue
                val title = ep.optNullableString("title") ?: KitsugiStrings.get(R.string.inline_episode_1_s_dc662be, i + 1)
                val thumb = ep.optNullableString("thumbnail")
                val url = ep.optNullableString("url")
                val site = ep.optNullableString("site")
                val epNum = parseEpisodeNumberFromTitle(title) ?: (i + 1)
                list.add(
                    KitsugiStreamingEpisode(
                        title = title,
                        thumbnail = thumb,
                        url = url,
                        site = site,
                        seasonNumber = 1,
                        episodeNumber = epNum
                    )
                )
            }
            list
        }.getOrElse { emptyList() }
    }

    private fun parseEpisodeNumberFromTitle(title: String): Int? {
        val patterns = listOf(
            Regex("""[Ss]\d+[Ee](\d+)"""),
            Regex("""[Ee]p(?:isode)?\.?\s*(\d+)""", RegexOption.IGNORE_CASE),
            Regex("""#(\d+)"""),
            Regex("""B[öo]l[üu]m\s+(\d+)""", RegexOption.IGNORE_CASE),
            Regex("""^(\d+)\s*[-–—.]"""),
            Regex("""\b(\d+)\b""")
        )
        for (pattern in patterns) {
            val num = pattern.find(title)?.groupValues?.getOrNull(1)?.toIntOrNull()
            if (num != null && num > 0 && num < 5000) return num
        }
        return null
    }
}
