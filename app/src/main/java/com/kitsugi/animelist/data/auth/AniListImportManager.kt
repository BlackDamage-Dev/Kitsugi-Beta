package com.kitsugi.animelist.data.auth

import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import com.kitsugi.animelist.data.remote.optNullableString
import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.R

object AniListImportManager {
    suspend fun fetchAnimeList(
        accessToken: String
    ): List<MediaEntry> {
        return withContext(Dispatchers.IO) {
            val viewer = fetchViewer(accessToken)
            fetchMediaEntries(
                accessToken = accessToken,
                userId = viewer.userId,
                mediaType = "ANIME"
            )
        }
    }

    suspend fun fetchMangaList(
        accessToken: String
    ): List<MediaEntry> {
        return withContext(Dispatchers.IO) {
            val viewer = fetchViewer(accessToken)
            fetchMediaEntries(
                accessToken = accessToken,
                userId = viewer.userId,
                mediaType = "MANGA"
            )
        }
    }

    suspend fun fetchAllLists(
        accessToken: String
    ): List<MediaEntry> {
        // Soğuma (429 cezası) aktifse hiç başlamayalım: sayfa aralarında dakikalarca bekleyip
        // yarım liste bırakmak yerine kullanıcıya net bilgi verilir (AuthViewModel bunu
        // "liste %1$d sn sonra yeniden çekilebilir" olarak gösterir).
        val cooldown = AniListGate.cooldownRemainingMs()
        if (cooldown > 5_000L) {
            throw IllegalStateException(AniListGate.quotaMessage(cooldown))
        }
        return withContext(Dispatchers.IO) {
            val viewer = fetchViewer(accessToken)
            val anime = fetchMediaEntries(accessToken, viewer.userId, "ANIME")
            val manga = fetchMediaEntries(accessToken, viewer.userId, "MANGA")
            anime + manga
        }
    }

    // ─── Önbellekler (v2.4.341) ──────────────────────────────────────────────
    // Liste yenileme her seferinde 1 Viewer + 1 profil isteğiyle başlıyordu; ikisi de
    // neredeyse hiç değişmez. 30 istek/dk bütçesinde bu 2 istek, bir sayfalık liste
    // çekiminden çalınan kotaydı. Artık bellek içinde önbelleklenir.
    private const val VIEWER_CACHE_MS = 24L * 60L * 60L * 1_000L
    private const val PROFILE_CACHE_MS = 15L * 60L * 1_000L

    @Volatile private var cachedViewerToken: String? = null
    @Volatile private var cachedViewer: ViewerInfo? = null
    @Volatile private var cachedViewerAt: Long = 0L

    @Volatile private var cachedProfileToken: String? = null
    @Volatile private var cachedProfile: AniListUserProfile? = null
    @Volatile private var cachedProfileAt: Long = 0L

    @Synchronized
    private fun fetchViewer(
        accessToken: String
    ): ViewerInfo {
        val now = System.currentTimeMillis()
        cachedViewer?.let { viewer ->
            if (cachedViewerToken == accessToken && now - cachedViewerAt < VIEWER_CACHE_MS) {
                return viewer
            }
        }

        val query = """
            query {
                Viewer {
                    id
                    name
                    avatar {
                        large
                    }
                }
            }
        """.trimIndent()

        val response = postGraphQl(
            accessToken = accessToken,
            query = query,
            variables = JSONObject()
        )

        val viewer = JSONObject(response)
            .getJSONObject("data")
            .getJSONObject("Viewer")

        val info = ViewerInfo(
            userId = viewer.getInt("id"),
            name = viewer.optNullableString("name") ?: "Bilinmeyen",
            avatarUrl = viewer.optJSONObject("avatar")?.optNullableString("large")
        )
        cachedViewerToken = accessToken
        cachedViewer = info
        cachedViewerAt = now
        return info
    }

    private fun fetchMediaEntries(
        accessToken: String,
        userId: Int,
        mediaType: String
    ): List<MediaEntry> {
        val allEntries = mutableListOf<MediaEntry>()
        var page = 1
        var hasNextPage = true

        val query = """
            query (${'$'}userId: Int, ${'$'}page: Int) {
                Page(page: ${'$'}page, perPage: 50) {
                    pageInfo {
                        hasNextPage
                    }
                    mediaList(userId: ${'$'}userId, type: $mediaType, sort: [MEDIA_ID]) {
                        id
                        status
                        progress
                        progressVolumes
                        score(format: POINT_10_DECIMAL)
                        scoreRaw: score(format: POINT_100)
                        advancedScores
                        private
                        notes
                        repeat
                        hiddenFromStatusLists
                        updatedAt
                        startedAt {
                            year
                            month
                            day
                        }
                        completedAt {
                            year
                            month
                            day
                        }
                        media {
                            id
                            idMal
                            type
                            format
                            episodes
                            chapters
                            seasonYear
                            startDate {
                                year
                            }
                            title {
                                romaji
                                english
                                native
                            }
                            coverImage {
                                large
                                medium
                            }
                            isFavourite
                            isAdult
                            description(asHtml: false)
                            genres
                        }
                    }
                }
            }
        """.trimIndent()

        while (hasNextPage) {
            val variables = JSONObject()
                .put("userId", userId)
                .put("page", page)

            val response = postGraphQl(
                accessToken = accessToken,
                query = query,
                variables = variables
            )

            val pageObj = JSONObject(response)
                .getJSONObject("data")
                .getJSONObject("Page")

            hasNextPage = pageObj
                .getJSONObject("pageInfo")
                .getBoolean("hasNextPage")

            val mediaList = pageObj.getJSONArray("mediaList")

            for (index in 0 until mediaList.length()) {
                val item = mediaList.getJSONObject(index)
                val media = item.getJSONObject("media")

                // list entry ID — SaveMediaListEntry mutation'larında direkt id olarak kullanılır
                val aniListEntryId = item.optInt("id", 0).takeIf { it > 0 }

                val mediaId = media.optInt("id", 0)
                val stableId = media.optInt("idMal", 0).takeIf { it > 0 }
                    ?: (100_000_000 + mediaId)

                val titleObject = media.getJSONObject("title")
                val romaji = titleObject.optNullableString("romaji")
                val english = titleObject.optNullableString("english")
                val native = titleObject.optNullableString("native")
                val title = romaji ?: english ?: native ?: KitsugiStrings.get(R.string.basliksiz)

                val format = media.optNullableString("format").orEmpty()
                    .replace("_", " ")
                    .lowercase()
                    .replaceFirstChar { it.uppercase() }

                // seasonYear manga ve bazı eski/özel yapımlar için boş döner; startDate.year ile tamamla.
                // Yıl bilgisi çapraz eşitlemede kimlik doğrulaması (remake/sezon ayrımı) için kullanılıyor.
                val year = media.optInt("seasonYear", 0).takeIf { it > 0 }
                    ?: media.optJSONObject("startDate")?.optInt("year", 0)?.takeIf { it > 0 }
                val genres = media.optJSONArray("genres")
                val genreText = buildList {
                    if (format.isNotBlank()) add(format)
                    if (year != null) add(year.toString())
                    if (genres != null) {
                        for (i in 0 until minOf(genres.length(), 3)) {
                            val gen = genres.optString(i)
                            if (gen.isNotBlank() && gen != "null") add(gen)
                        }
                    }
                }.joinToString(", ")

                val imageUrl = media.optJSONObject("coverImage")?.optNullableString("large")
                    ?: media.optJSONObject("coverImage")?.optNullableString("medium")

                // AniList puanı: POINT_10_DECIMAL (7.5), raw score veya advancedScores
                val scoreDouble = item.optDouble("score", 0.0)
                val scoreRaw = item.optDouble("scoreRaw", 0.0)
                val effectiveScoreDouble = if (scoreDouble > 0.0) scoreDouble else scoreRaw
                var score = if (effectiveScoreDouble > 0.0) {
                    if (effectiveScoreDouble > 10.0) {
                        (effectiveScoreDouble / 10.0).toBigDecimal().setScale(0, java.math.RoundingMode.HALF_UP).toInt().coerceIn(1, 10)
                    } else {
                        effectiveScoreDouble.toBigDecimal().setScale(0, java.math.RoundingMode.HALF_UP).toInt().coerceIn(1, 10)
                    }
                } else {
                    null
                }
                if (score == null) {
                    val adv = item.optJSONObject("advancedScores")
                    if (adv != null) {
                        val vals = adv.keys().asSequence().mapNotNull { k ->
                            adv.optDouble(k, 0.0).takeIf { it > 0.0 }
                        }.toList()
                        if (vals.isNotEmpty()) {
                            val avg = vals.average()
                            score = if (avg > 10.0) {
                                (avg / 10.0).toBigDecimal().setScale(0, java.math.RoundingMode.HALF_UP).toInt().coerceIn(1, 10)
                            } else {
                                avg.toBigDecimal().setScale(0, java.math.RoundingMode.HALF_UP).toInt().coerceIn(1, 10)
                            }
                        }
                    }
                }

                val startDate = item.optJSONObject("startedAt").toLocalDateString()
                val endDate = item.optJSONObject("completedAt").toLocalDateString()

                val isPrivate = item.optBoolean("private", false)
                val notes = item.optNullableString("notes")
                val repeatCount = item.optInt("repeat", 0)
                val volumeProgress = item.optInt("progressVolumes", 0)
                val isHiddenFromStatusLists = item.optBoolean("hiddenFromStatusLists", false)
                val updatedAt = item.optLong("updatedAt", 0L)
                val titleEnglish = english
                val titleJapanese = native

                val entry = MediaEntry(
                    id = 0,
                    title = title,
                    subtitle = if (genreText.isBlank()) {
                        KitsugiStrings.get(R.string.anilist_ten_ice_aktarildi)
                    } else {
                        genreText
                    },
                    type = if (media.optNullableString("type") == "MANGA") MediaType.Manga else MediaType.Anime,
                    status = mapAniListStatus(item.optNullableString("status").orEmpty()),
                    score = score,
                    progress = item.optInt("progress", 0),
                    total = (media.optInt("episodes", 0).takeIf { it > 0 }
                        ?: media.optInt("chapters", 0).takeIf { it > 0 }),
                    isFavorite = media.optBoolean("isFavourite", false),
                    isAdult = media.optBoolean("isAdult", false),
                    source = "anilist",
                    malId = stableId,
                    imageUrl = imageUrl,
                    year = year,
                    synopsis = media.optNullableString("description")
                        ?.cleanApiText()
                        ?.takeIf { it.isNotBlank() },
                    startDate = startDate,
                    endDate = endDate,
                    notes = notes,
                    isPrivate = isPrivate,
                    isHiddenFromStatusLists = isHiddenFromStatusLists,
                    isRepeating = repeatCount > 0,
                    repeatCount = repeatCount,
                    volumeProgress = volumeProgress,
                    updatedAt = updatedAt,
                    titleEnglish = titleEnglish,
                    titleJapanese = titleJapanese,
                    aniListEntryId = aniListEntryId
                )

                allEntries.add(entry)
            }

            page++
        }

        return allEntries
    }

    private fun postGraphQl(
        accessToken: String,
        query: String,
        variables: JSONObject
    ): String {
        val payload = JSONObject()
            .put("query", query)
            .put("variables", variables)
            .toString()

        val request = Request.Builder()
            .url("https://graphql.anilist.co")
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $accessToken")
            .build()

        try {
            // BULK önceliği: kullanıcı ilerlemeyi görüyor, liste çekimi dakikalar sürebilir.
            // Kota kapısı sayesinde sayfalar 30/dk sınırının altında tempolu gider.
            // 20 sn'den uzun sıra beklemesi: kullanıcıya dönen bir spinner bırakmak yerine
            // "kota doldu, N sn sonra tekrar dene" demek daha doğru.
            return AniListGate.withPermitBlocking(
                priority = AniListGate.Priority.BULK,
                maxWaitMs = 40_000L
            ) {
                com.kitsugi.animelist.core.network.KitsugiHttpClient.aniListClient.newCall(request).execute().use { response ->
                    val body = runCatching { response.body?.string().orEmpty() }.getOrDefault("")
                    AniListGate.onResponse(response.code, response.headers, body)

                    com.kitsugi.animelist.data.remote.KitsugiApiBase
                        .extractAniListServiceDownMessage(body)
                        ?.let { downMsg ->
                            val down = com.kitsugi.animelist.data.remote.KitsugiApiBase
                                .recordAniListServiceDown(downMsg, response.code)
                            throw IllegalStateException(down.message, down)
                        }

                    if (!response.isSuccessful) {
                        if (response.code == 429) {
                            val waitMs = AniListGate.cooldownRemainingMs()
                            throw AniListGate.QuotaExhaustedException(waitMs, AniListGate.quotaMessage(waitMs))
                        }
                        throw IllegalStateException("AniList API hatası: ${response.code} $body")
                    }

                    val errors = JSONObject(body).optJSONArray("errors")
                    require(errors == null || errors.length() == 0) { "AniList GraphQL liste okuma hatası: $errors" }
                    body
                }
            }
        } catch (quota: AniListGate.QuotaExhaustedException) {
            // Kota: liste yenileme iptal edilir, kullanıcıya bekleme süresi söylenir.
            throw IllegalStateException(quota.message, quota)
        } catch (e: Exception) {
            if (e is IllegalStateException) throw e
            throw IllegalStateException("AniList bağlantı hatası: ${e.message}", e)
        }
    }

    private fun mapAniListStatus(
        status: String
    ): WatchStatus {
        return when (status) {
            "CURRENT" -> WatchStatus.Watching
            "COMPLETED" -> WatchStatus.Completed
            "DROPPED" -> WatchStatus.Dropped
            "PLANNING" -> WatchStatus.Planned
            "PAUSED" -> WatchStatus.Paused
            else -> WatchStatus.Planned
        }
    }

    private fun JSONObject?.toLocalDateString(): String? {
        if (this == null) return null

        val year = optInt("year", 0)
        val month = optInt("month", 0)
        val day = optInt("day", 0)

        if (year <= 0 || month <= 0 || day <= 0) return null

        return "%04d-%02d-%02d".format(year, month, day)
    }

    private fun String.cleanApiText(): String {
        return this
            .replace("<br>", "\n")
            .replace("<br />", "\n")
            .replace("<i>", "")
            .replace("</i>", "")
            .replace("<b>", "")
            .replace("</b>", "")
            .replace(Regex("<.*?>"), "")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&apos;", "'")
            .trim()
    }

    private data class ViewerInfo(
        val userId: Int,
        val name: String,
        val avatarUrl: String?
    )

    data class AniListUserProfile(
        val name: String,
        val avatarUrl: String?,
        val bannerUrl: String?
    )

    suspend fun fetchUserProfile(
        accessToken: String
    ): AniListUserProfile {
        val now = System.currentTimeMillis()
        cachedProfile?.let { profile ->
            if (cachedProfileToken == accessToken && now - cachedProfileAt < PROFILE_CACHE_MS) {
                return profile
            }
        }
        return withContext(Dispatchers.IO) {
            val query = """
                query {
                    Viewer {
                        name
                        avatar {
                            large
                        }
                        bannerImage
                    }
                }
            """.trimIndent()

            val response = postGraphQl(
                accessToken = accessToken,
                query = query,
                variables = JSONObject()
            )

            val viewer = JSONObject(response)
                .getJSONObject("data")
                .getJSONObject("Viewer")

            val profile = AniListUserProfile(
                name = viewer.optNullableString("name") ?: KitsugiStrings.get(R.string.ui_profile_anilist_fallback),
                avatarUrl = viewer.optJSONObject("avatar")?.optNullableString("large"),
                bannerUrl = viewer.optNullableString("bannerImage")
            )
            cachedProfileToken = accessToken
            cachedProfile = profile
            cachedProfileAt = now
            profile
        }
    }
}