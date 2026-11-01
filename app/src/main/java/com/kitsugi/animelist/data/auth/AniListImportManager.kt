package com.kitsugi.animelist.data.auth

import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

    // ─── Yeniden deneme politikası (içe aktarım) ───────────────────────────────
    // Liste içe aktarımı onlarca istektir (1 Viewer + N sayfa × 2 tür). Eski kod
    // hiç beklemeden hepsini arka arkaya gönderiyor ve TEK bir 429'da tüm işi
    // çökertiyordu → Listem kalıcı olarak boş kalıyordu. Artık her istek ortak
    // hız bütçesinden (AniListGovernor) geçer; 429 gelirse soğuma beklenir ve
    // sayfa yeniden denenir. Hiçbir sayfa alınamadıysa hata fırlatılır; en az bir
    // sayfa alındıysa KISMİ sonuç döndürülür (boş liste göstermekten iyidir).
    private const val IMPORT_MAX_ATTEMPTS = 4
    private const val IMPORT_COOLDOWN_WAIT_MS = 70_000L

    /** AniList istek sınırına takıldık: kullanıcıya süre bildirilir, iş kuyrukta bekler. */
    class AniListRateLimitException(val retryAfterSeconds: Long) : IllegalStateException(
        "AniList istek sınırı doldu — $retryAfterSeconds sn sonra otomatik tekrar denenecek"
    )

    /** AniList sunucu şalteri indi: yeniden denemek 45 sn boyunca anlamsız. */
    class AniListUnavailableException(message: String) : IllegalStateException(message)

    /** AniList oturumu geçersiz: kullanıcı yeniden giriş yapmalı. */
    class AniListAuthException(message: String) : IllegalStateException(message)

    /**
     * İçe aktarım sonucu.
     *
     * @param entries   çekilebilen kayıtlar
     * @param complete  `false` ise aktarım yarıda kaldı (hız sınırı / kesinti) ve
     *                  liste TAM değil — çağıran mevcut satırları SİLMEMELİDİR.
     */
    data class ImportResult(
        val entries: List<MediaEntry>,
        val complete: Boolean,
        val errorMessage: String? = null,
        val rateLimited: Boolean = false
    )

    suspend fun fetchAnimeList(
        accessToken: String
    ): List<MediaEntry> {
        return withContext(Dispatchers.IO) {
            val viewer = fetchViewer(accessToken)
            fetchMediaEntries(
                accessToken = accessToken,
                userId = viewer.userId,
                mediaType = "ANIME"
            ).entries
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
            ).entries
        }
    }

    /**
     * **Fark (delta) çekimi**: yalnızca [sinceEpochSeconds]'ten sonra değişen liste
     * kayıtlarını getirir (AniList `mediaList(updatedAt_greater: ...)` süzgeci).
     *
     * Neden: liste yenilemesi her seferinde tüm sayfaları (kullanıcı başına yüzlerce
     * kayıt, 50'lik sayfalar) baştan çekiyordu. Kullanıcı yalnızca 1 bölüm işaretlediyse
     * değişen tek satırı almak yeterli; tam uzlaştırma (silinenleri de yakalamak için)
     * periyodik olarak [ListRefreshPolicy] tarafından yapılır.
     *
     * NOT: Delta silmeleri GÖREMEZ. Bu yüzden çağıran taraf `allowDelete = false` ile
     * birleştirmelidir; silme yalnızca tam senkron turunda ele alınır.
     *
     * @throws IllegalStateException şema/parametre reddederse (çağıran tam listeye düşer)
     */
    suspend fun fetchListsSince(
        accessToken: String,
        sinceEpochSeconds: Long
    ): List<MediaEntry> {
        return withContext(Dispatchers.IO) {
            val viewer = fetchViewer(accessToken)
            val since = sinceEpochSeconds.coerceAtLeast(1L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val anime = fetchMediaEntries(accessToken, viewer.userId, "ANIME", since)
            val manga = fetchMediaEntries(accessToken, viewer.userId, "MANGA", since)
            anime.entries + manga.entries
        }
    }

    suspend fun fetchAllLists(
        accessToken: String
    ): List<MediaEntry> = fetchAllListsDetailed(accessToken).entries

    /**
     * Anime + manga listesini çeker.
     *
     * Hız sınırı / kesinti yüzünden aktarım yarıda kalırsa o ana kadar toplanan
     * kayıtlar [ImportResult.complete = false] ile döndürülür; hiçbir sayfa
     * alınamadıysa istisna fırlatılır (çağıran kullanıcıya net hata gösterir).
     */
    suspend fun fetchAllListsDetailed(
        accessToken: String
    ): ImportResult {
        return withContext(Dispatchers.IO) {
            val viewer = fetchViewer(accessToken)
            var complete = true
            var error: String? = null
            var rateLimited = false

            val anime = try {
                fetchMediaEntries(accessToken, viewer.userId, "ANIME")
            } catch (e: Exception) {
                captureImportFailure(e) { message, isRateLimit ->
                    complete = false
                    error = message
                    rateLimited = isRateLimit
                }
                PageResult(emptyList(), complete = false)
            }

            val manga = try {
                fetchMediaEntries(accessToken, viewer.userId, "MANGA")
            } catch (e: Exception) {
                captureImportFailure(e) { message, isRateLimit ->
                    complete = false
                    error = error ?: message
                    rateLimited = rateLimited || isRateLimit
                }
                PageResult(emptyList(), complete = false)
            }

            if (!anime.complete || !manga.complete) complete = false

            val entries = anime.entries + manga.entries
            if (entries.isEmpty() && !complete) {
                // Hiç kayıt alınamadı: sessizce "boş liste" yazdırmaktansa hatayı yükselt.
                throw error?.let { IllegalStateException(it) } ?: AniListUnavailableException(
                    "AniList listesi alınamadı"
                )
            }
            ImportResult(
                entries = entries,
                complete = complete,
                errorMessage = error,
                rateLimited = rateLimited
            )
        }
    }

    private inline fun captureImportFailure(
        error: Throwable,
        onFailure: (message: String, rateLimited: Boolean) -> Unit
    ) {
        if (error is kotlinx.coroutines.CancellationException) throw error
        onFailure(
            error.message ?: error.javaClass.simpleName,
            error is AniListRateLimitException
        )
    }

    private suspend fun fetchViewer(
        accessToken: String
    ): ViewerInfo {
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
            .optJSONObject("data")
            ?.optJSONObject("Viewer")
            ?: throw AniListAuthException(
                "AniList kullanıcı bilgisi alınamadı (oturum geçersiz olabilir)"
            )

        return ViewerInfo(
            userId = viewer.optInt("id", 0).takeIf { it > 0 }
                ?: throw AniListAuthException("AniList kullanıcı kimliği alınamadı"),
            name = viewer.optNullableString("name") ?: "Bilinmeyen",
            avatarUrl = viewer.optJSONObject("avatar")?.optNullableString("large")
        )
    }

    /** Tek tür (ANIME/MANGA) için sayfa sayfa çekilen sonuç. */
    private data class PageResult(
        val entries: List<MediaEntry>,
        val complete: Boolean
    )

    private suspend fun fetchMediaEntries(
        accessToken: String,
        userId: Int,
        mediaType: String,
        updatedSinceSec: Int? = null
    ): PageResult {
        val allEntries = mutableListOf<MediaEntry>()
        var page = 1
        var hasNextPage = true

        val deltaFilter = if (updatedSinceSec != null) {
            ", updatedAt_greater: ${'$'}updatedSince"
        } else {
            ""
        }
        val deltaVariable = if (updatedSinceSec != null) {
            ", ${'$'}updatedSince: Int"
        } else {
            ""
        }

        val query = """
            query (${'$'}userId: Int, ${'$'}page: Int$deltaVariable) {
                Page(page: ${'$'}page, perPage: 50) {
                    pageInfo {
                        hasNextPage
                    }
                    mediaList(userId: ${'$'}userId, type: $mediaType, sort: [MEDIA_ID]$deltaFilter) {
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

        // ── Sonsuz döngü koruması ──────────────────────────────────────────────
        // AniList zaman zaman SON sayfada `pageInfo.hasNextPage = true` gönderip
        // BOŞ bir `mediaList` döndürür. Eski kod yalnızca `hasNextPage`'e bakıyordu:
        // boş sayfaları sonsuza kadar istemeye devam ediyor, her turda kotadan
        // yiyordu. Sonuç: içe aktarım hiç bitmiyor ve kullanıcı ekranda kalıcı
        // olarak "AniList istek sınırı doldu" görüyordu.
        //   • sayfa tavanı (200 × 50 = 10.000 kayıt): gerçek bir liste için yeterli
        //   • üst üste 2 boş sayfa: akış bitti demektir, dur
        val MAX_PAGES = 200
        var emptyPageStreak = 0

        while (hasNextPage && page <= MAX_PAGES) {
            val variables = JSONObject()
                .put("userId", userId)
                .put("page", page)
            if (updatedSinceSec != null) {
                variables.put("updatedSince", updatedSinceSec)
            }

            val response = try {
                postGraphQl(
                    accessToken = accessToken,
                    query = query,
                    variables = variables
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                // En az bir sayfa alındıysa KISMİ sonuç döndür: hiç yoktan iyidir ve
                // Listem'in kalıcı boş kalmasını engeller. Hiç sayfa alınamadıysa
                // hata yukarı çıkar, çağıran kullanıcıya net mesaj verir.
                if (allEntries.isEmpty()) throw e
                android.util.Log.w(
                    "AniListImport",
                    "$mediaType içe aktarımı sayfa $page'da yarıda kaldı (${allEntries.size} kayıt alındı): ${e.message}"
                )
                return PageResult(allEntries, complete = false)
            }

            val pageObj = JSONObject(response)
                .getJSONObject("data")
                .getJSONObject("Page")

            hasNextPage = pageObj
                .getJSONObject("pageInfo")
                .getBoolean("hasNextPage")

            val mediaList = pageObj.getJSONArray("mediaList")

            // Boş sayfa sayacı: AniList "hasNextPage: true" diyorsa ama kayıt
            // göndermiyorsa akış gerçekte bitmiştir.
            if (mediaList.length() == 0) {
                emptyPageStreak++
                if (emptyPageStreak >= 2) {
                    android.util.Log.w(
                        "AniListImport",
                        "$mediaType: üst üste 2 boş sayfa (sayfa $page) — içe aktarım sonlandırıldı."
                    )
                    break
                }
            } else {
                emptyPageStreak = 0
            }

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

        return PageResult(allEntries, complete = true)
    }

    /**
     * AniList'e GraphQL isteği atar.
     *
     * Eski sürüm doğrudan OkHttp çağırıyordu: ne hız sınırlaması (onlarca sayfa
     * arka arkaya), ne 429 yeniden denemesi, ne de GraphQL katmanındaki
     * `errors[status=429]` kontrolü vardı. Bu yüzden tek bir hız sınırı yanıtı
     * tüm içe aktarımı çökertiyor, Listem hiç dolmuyordu.
     *
     * Şimdi: ortak bütçe (AniListGovernor) → istek → HTTP + GraphQL sınıflandırma
     * → 429/5xx için soğumayı bekle ve yeniden dene.
     */
    private suspend fun postGraphQl(
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

        var attempt = 0
        var lastFailure: Throwable? = null

        while (attempt < IMPORT_MAX_ATTEMPTS) {
            attempt++

            // 1) Ortak hız bütçesinden slot al (gerekirse 429 soğumasını bekle).
            when (val gate = AniListGovernor.acquire(maxWaitMs = IMPORT_COOLDOWN_WAIT_MS)) {
                is AniListGovernor.Gate.Proceed -> Unit
                is AniListGovernor.Gate.Blocked -> when (gate.reason) {
                    AniListGovernor.BlockedReason.RATE_LIMITED ->
                        throw AniListRateLimitException((gate.retryAfterMs / 1000L).coerceAtLeast(1L))
                    AniListGovernor.BlockedReason.SERVICE_DOWN ->
                        throw AniListUnavailableException(
                            "AniList geçici olarak devre dışı: ${AniListGovernor.serviceDownMessage()}"
                        )
                    AniListGovernor.BlockedReason.GATEWAY_DOWN ->
                        throw IllegalStateException("AniList şu anda ulaşılamıyor (ağ/geçit hatası)")
                }
            }

            // 2) İsteği gönder (IO dispatcher'ındayız; bloklayan çağrı güvenli).
            val call = try {
                com.kitsugi.animelist.core.network.KitsugiHttpClient.aniListClient.newCall(request)
                    .execute()
                    .use { response ->
                        val body = runCatching { response.body?.string() ?: "" }.getOrDefault("")
                        response to body
                    }
            } catch (e: Exception) {
                if (e is java.io.InterruptedIOException) throw e
                AniListGovernor.observeTransportFailure(e)
                lastFailure = e
                if (attempt >= IMPORT_MAX_ATTEMPTS) break
                delay(1_500L * attempt)
                continue
            }

            val (response, body) = call

            // 3) HTTP katmanı.
            when (val outcome = AniListGovernor.observeResponse(response, body)) {
                is AniListGovernor.Outcome.Success -> {
                    // 4) GraphQL katmanı: HTTP 200 gelse de `errors` içinde 429 olabilir.
                    val errors = runCatching { JSONObject(body).optJSONArray("errors") }.getOrNull()
                    when (val classified = AniListGovernor.classifyErrors(errors)) {
                        is AniListGovernor.Outcome.Success -> return body
                        is AniListGovernor.Outcome.RateLimited -> {
                            lastFailure = AniListRateLimitException(classified.retryAfterMs / 1000L)
                            if (attempt >= IMPORT_MAX_ATTEMPTS) break
                            continue // acquire() soğumayı bekleyecek
                        }
                        is AniListGovernor.Outcome.AuthFailure ->
                            throw AniListAuthException("AniList oturum süresi dolmuş; lütfen hesabı yeniden bağlayın.")
                        is AniListGovernor.Outcome.ServiceDown ->
                            throw AniListUnavailableException("AniList geçici olarak devre dışı: ${classified.message}")
                        is AniListGovernor.Outcome.Retryable -> {
                            lastFailure = IllegalStateException("AniList geçici hata (${classified.retryAfterMs}ms)")
                            if (attempt >= IMPORT_MAX_ATTEMPTS) break
                            delay(classified.retryAfterMs.coerceIn(1_000L, 5_000L))
                        }
                        is AniListGovernor.Outcome.Permanent ->
                            throw IllegalStateException("AniList GraphQL liste okuma hatası: ${errors ?: body.take(200)}")
                    }
                }
                is AniListGovernor.Outcome.RateLimited -> {
                    lastFailure = AniListRateLimitException(outcome.retryAfterMs / 1000L)
                    if (attempt >= IMPORT_MAX_ATTEMPTS) break
                    continue
                }
                is AniListGovernor.Outcome.Retryable -> {
                    lastFailure = IllegalStateException("AniList geçici sunucu hatası")
                    if (attempt >= IMPORT_MAX_ATTEMPTS) break
                    delay(outcome.retryAfterMs.coerceIn(1_000L, 5_000L))
                }
                is AniListGovernor.Outcome.AuthFailure ->
                    throw AniListAuthException("AniList oturum süresi dolmuş; lütfen hesabı yeniden bağlayın.")
                is AniListGovernor.Outcome.ServiceDown ->
                    throw AniListUnavailableException("AniList geçici olarak devre dışı: ${outcome.message}")
                is AniListGovernor.Outcome.Permanent ->
                    throw IllegalStateException("AniList API hatası: ${response.code} ${body.take(200)}")
            }
        }

        throw lastFailure ?: IllegalStateException("AniList API hatası: liste alınamadı")
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
                .optJSONObject("data")
                ?.optJSONObject("Viewer")
                ?: throw AniListAuthException(
                    "AniList profil bilgisi alınamadı (oturum geçersiz olabilir)"
                )

            AniListUserProfile(
                name = viewer.optNullableString("name") ?: KitsugiStrings.get(R.string.ui_profile_anilist_fallback),
                avatarUrl = viewer.optJSONObject("avatar")?.optNullableString("large"),
                bannerUrl = viewer.optNullableString("bannerImage")
            )
        }
    }
}