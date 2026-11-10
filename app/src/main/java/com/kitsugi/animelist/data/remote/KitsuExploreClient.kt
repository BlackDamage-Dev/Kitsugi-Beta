package com.kitsugi.animelist.data.remote

import android.util.Log
import com.kitsugi.animelist.core.network.KitsugiHttpClient
import com.kitsugi.animelist.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.R

/**
 * Kitsu.io REST API — AniList keşfet sayfası için ücretsiz fallback.
 *
 * Kullanım senaryosu:
 *  - AniList hesabı bağlı DEĞİL ve AniList tab seçili → Kitsu ile doldur
 *  - AniList API geçici olarak kapalı (AniListServiceDownException) → Kitsu'ya düş
 *
 * Kitsu API özellikleri:
 *  - Base URL: [KitsuApiHost] üzerinden çözülür (kitsu.app kanonik, kitsu.io yedek)
 *  - Format: JSON:API (application/vnd.api+json)
 *  - Auth: GET istekleri için gerekmiyor (public keşfet)
 *  - Rate Limit: Belirtilmemiş; yavaş-sabırlı istek yapılması önerilir
 *
 * ID Stratejisi:
 *  - Kitsu anime numeric ID'si kullanılır (örn. 7936)
 *  - stableId = kitsuId + 300_000_000 (MAL/AniList offset'leriyle çakışmaz)
 *  - source = "kitsu"
 *  - Detail sayfası: KitsugiDetailClient "kitsu" source'u KitsuClient üzerinden handle eder
 */
object KitsuExploreClient {
    private const val TAG = "KitsuExploreClient"
    private const val KITSU_ID_OFFSET = 300_000_000

    /**
     * Sayfa başına kayıt tavanı. Kitsu `page[limit]` için 20'den büyük değeri
     * `400 Invalid page value → Limit exceeds maximum page size of 20` ile reddeder.
     */
    private const val KITSU_MAX_PAGE_LIMIT = 20

    /**
     * `page[offset]` desteklemeyen host'ta (kitsu.io uyumluluk katmanı) 2. sayfanın 1.
     * sayfayla birebir aynı olduğunu anlayabilmek için sorgu başına ilk kaydın kimliği.
     */
    private val pageAnchors = com.kitsugi.animelist.core.memory.BoundedCache<String, String>(
        name = "kitsu_page_anchor", maxEntries = 64
    )

    // ── Public API ───────────────────────────────────────────────────────────
    
    /** Kitsu API üzerinden anime/manga araması (AniList için fallback) */
    suspend fun searchAnime(query: String, mediaType: MediaType, limit: Int = 20): List<JikanSearchResult> =
        withContext(Dispatchers.IO) {
            val endpoint = when (mediaType) {
                MediaType.Anime, MediaType.Movie, MediaType.TvShow -> "anime"
                MediaType.Manga -> "manga"
            }
            val safeLimit = limit.coerceIn(1, 20)
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            fetchPaged("/$endpoint?filter[text]=$encoded&page[limit]=$safeLimit", mediaType)
        }

    suspend fun searchMediaAdvanced(
        mediaType: MediaType,
        query: String = "",
        page: Int = 1,
        limit: Int = 20,
        sort: String = "trending",
        subtypes: List<String>? = null,
        statuses: List<String>? = null,
        season: String? = null,
        seasonYear: Int? = null,
        categories: List<String>? = null,
        ageRating: String? = null,
        streamers: List<String>? = null,
        minRating: Int? = null
    ): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        val endpoint = when (mediaType) {
            MediaType.Anime, MediaType.Movie, MediaType.TvShow -> "anime"
            MediaType.Manga -> "manga"
        }
        val safeLimit = limit.coerceIn(1, 20)
        val offset = (page - 1) * safeLimit
        val params = mutableListOf<String>()
        params.add("page[limit]=$safeLimit")
        params.add("page[offset]=$offset")

        if (query.isNotBlank()) params.add("filter[text]=${java.net.URLEncoder.encode(query.trim(), "UTF-8")}")
        if (sort != "trending") params.add("sort=$sort")
        if (!subtypes.isNullOrEmpty()) params.add("filter[subtype]=${subtypes.joinToString(",")}")
        if (!statuses.isNullOrEmpty()) params.add("filter[status]=${statuses.joinToString(",")}")
        if (!season.isNullOrBlank()) params.add("filter[season]=${season.lowercase()}")
        if (seasonYear != null) params.add("filter[seasonYear]=$seasonYear")
        if (!categories.isNullOrEmpty()) params.add("filter[categories]=${categories.joinToString(",")}")
        if (!ageRating.isNullOrBlank()) params.add("filter[ageRating]=$ageRating")
        if (!streamers.isNullOrEmpty()) params.add("filter[streamers]=${streamers.joinToString(",")}")
        if (minRating != null && minRating > 0) params.add("filter[averageRating]=$minRating..100")

        fetchPaged("/$endpoint?${params.joinToString("&")}", mediaType)
    }

    suspend fun searchCharacters(query: String, page: Int = 1, limit: Int = 20): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val safeLimit = limit.coerceIn(1, 20)
        val offset = (page - 1) * safeLimit
        val encoded = java.net.URLEncoder.encode(query.trim(), "UTF-8")
        val url = KitsuApiHost.url("/characters?filter[name]=$encoded&page[limit]=$safeLimit&page[offset]=$offset")
        val req = Request.Builder().url(url).header("Accept", "application/vnd.api+json").build()
        try {
            KitsugiHttpClient.client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@withContext emptyList()
                val root = JSONObject(res.body?.string().orEmpty())
                val data = root.optJSONArray("data") ?: return@withContext emptyList()
                val list = mutableListOf<JikanSearchResult>()
                for (i in 0 until data.length()) {
                    val item = data.getJSONObject(i)
                    val id = item.optInt("id", 0)
                    if (id <= 0) continue
                    val attrs = item.optJSONObject("attributes") ?: continue
                    val name = attrs.optString("canonicalName", attrs.optString("name", "Karakter"))
                    val imageObj = attrs.optJSONObject("image")
                    val imgUrl = imageObj?.optNullableString("original") ?: imageObj?.optNullableString("medium")
                    list.add(
                        JikanSearchResult(
                            malId = id,
                            title = name,
                            subtitle = "Karakter (Kitsu)",
                            type = MediaType.Anime,
                            total = null,
                            score = null,
                            isAdult = false,
                            imageUrl = imgUrl,
                            year = null,
                            source = "kitsu"
                        )
                    )
                }
                list
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Trend animeler (Kitsu trending endpoint + sayfalı devam).
     *
     * Kitsu'nun `TrendingController` ucu (`/trending/anime`) JSON:API `page[limit]` ve
     * `page[offset]` parametrelerini yok sayar ve sabit 10 kayıt döndürür (ayrıca üst seviye
     * `limit` parametresi sunucuda String olarak okunduğundan `limit - 1` hatasına yol açar).
     * Bu nedenle:
     *  - 1. sayfada (`offset == 0`) `/trending/anime` çağrılır ve `limit` değerine ulaşmak
     *    için popüler yayındaki animelerle (`airingAnime` / `topAnime`) tamamlanır.
     *  - Sonraki sayfalarda (`offset > 0`) popüler yayındaki animelerle (`airingAnime`,
     *    yedek olarak `topAnime`) kesintisiz sayfalamaya devam edilir.
     */
    suspend fun trendingAnime(limit: Int = 20, offset: Int = 0): List<JikanSearchResult> =
        withContext(Dispatchers.IO) {
            val safeLimit = limit.coerceIn(1, KITSU_MAX_PAGE_LIMIT)
            val safeOffset = offset.coerceAtLeast(0)
            if (safeOffset == 0) {
                val primary = fetchAnimeList("/trending/anime")
                if (primary.size >= safeLimit) return@withContext primary.take(safeLimit)
                val supplement = airingAnime(safeLimit, offset = 0).ifEmpty { topAnime(safeLimit, offset = 0) }
                (primary + supplement).distinctBy { it.malId }.take(safeLimit)
            } else {
                val airing = airingAnime(safeLimit, offset = safeOffset)
                if (airing.isNotEmpty()) airing else topAnime(safeLimit, offset = safeOffset)
            }
        }

    /** Sezonluk animeler (belirtilen mevsim ve yıl) */
    suspend fun seasonalAnime(season: String, year: Int, limit: Int = 20, offset: Int = 0): List<JikanSearchResult> {
        val safeLimit = limit.coerceIn(1, KITSU_MAX_PAGE_LIMIT)
        val safeOffset = offset.coerceAtLeast(0)
        return fetchAnimeList("/anime?filter[season]=${season.lowercase()}&filter[seasonYear]=$year&sort=-userCount&page[limit]=$safeLimit&page[offset]=$safeOffset")
    }

    /** En yüksek puanlı animeler */
    suspend fun topRatedAnime(limit: Int = 20, offset: Int = 0): List<JikanSearchResult> {
        val safeLimit = limit.coerceIn(1, KITSU_MAX_PAGE_LIMIT)
        val safeOffset = offset.coerceAtLeast(0)
        return fetchAnimeList("/anime?sort=-averageRating&page[limit]=$safeLimit&page[offset]=$safeOffset")
    }

    /** En popüler animeler (userCount'a göre sıralı) */
    suspend fun topAnime(limit: Int = 20, offset: Int = 0): List<JikanSearchResult> {
        val safeLimit = limit.coerceIn(1, KITSU_MAX_PAGE_LIMIT)
        val safeOffset = offset.coerceAtLeast(0)
        return fetchAnimeList("/anime?sort=-userCount&page[limit]=$safeLimit&page[offset]=$safeOffset")
    }

    /** Yayında olan animeler */
    suspend fun airingAnime(limit: Int = 20, offset: Int = 0): List<JikanSearchResult> {
        val safeLimit = limit.coerceIn(1, KITSU_MAX_PAGE_LIMIT)
        val safeOffset = offset.coerceAtLeast(0)
        return fetchAnimeList("/anime?filter[status]=current&sort=-userCount&page[limit]=$safeLimit&page[offset]=$safeOffset")
    }

    /** Yakında yayınlanacak animeler */
    suspend fun upcomingAnime(limit: Int = 20, offset: Int = 0): List<JikanSearchResult> {
        val safeLimit = limit.coerceIn(1, KITSU_MAX_PAGE_LIMIT)
        val safeOffset = offset.coerceAtLeast(0)
        return fetchAnimeList("/anime?filter[status]=upcoming&sort=-userCount&page[limit]=$safeLimit&page[offset]=$safeOffset")
    }

    /** Yakın zamanda eklenen animeler */
    suspend fun newlyAddedAnime(limit: Int = 20, offset: Int = 0): List<JikanSearchResult> {
        val safeLimit = limit.coerceIn(1, KITSU_MAX_PAGE_LIMIT)
        val safeOffset = offset.coerceAtLeast(0)
        return fetchAnimeList("/anime?sort=-createdAt&page[limit]=$safeLimit&page[offset]=$safeOffset")
    }

    /** Film formatındaki animeler */
    suspend fun movieAnime(limit: Int = 20, offset: Int = 0): List<JikanSearchResult> {
        val safeLimit = limit.coerceIn(1, KITSU_MAX_PAGE_LIMIT)
        val safeOffset = offset.coerceAtLeast(0)
        return fetchAnimeList("/anime?filter[subtype]=movie&sort=-userCount&page[limit]=$safeLimit&page[offset]=$safeOffset")
    }

    /** En yüksek puanlı mangalar */
    suspend fun topRatedManga(limit: Int = 20, offset: Int = 0): List<JikanSearchResult> {
        val safeLimit = limit.coerceIn(1, KITSU_MAX_PAGE_LIMIT)
        val safeOffset = offset.coerceAtLeast(0)
        return fetchMangaList("/manga?sort=-averageRating&page[limit]=$safeLimit&page[offset]=$safeOffset")
    }

    /** En popüler mangalar */
    suspend fun topManga(limit: Int = 20, offset: Int = 0): List<JikanSearchResult> {
        val safeLimit = limit.coerceIn(1, KITSU_MAX_PAGE_LIMIT)
        val safeOffset = offset.coerceAtLeast(0)
        return fetchMangaList("/manga?sort=-userCount&page[limit]=$safeLimit&page[offset]=$safeOffset")
    }

    /** Yayında olan mangalar */
    suspend fun publishingManga(limit: Int = 20, offset: Int = 0): List<JikanSearchResult> {
        val safeLimit = limit.coerceIn(1, KITSU_MAX_PAGE_LIMIT)
        val safeOffset = offset.coerceAtLeast(0)
        return fetchMangaList("/manga?filter[status]=current&sort=-userCount&page[limit]=$safeLimit&page[offset]=$safeOffset")
    }

    /** Trend mangalar (favoritesCount sırası) */
    suspend fun trendingManga(limit: Int = 20, offset: Int = 0): List<JikanSearchResult> {
        val safeLimit = limit.coerceIn(1, KITSU_MAX_PAGE_LIMIT)
        val safeOffset = offset.coerceAtLeast(0)
        return fetchMangaList("/manga?sort=-favoritesCount&page[limit]=$safeLimit&page[offset]=$safeOffset")
    }

    /** Yakın zamanda eklenen mangalar */
    suspend fun newlyAddedManga(limit: Int = 20, offset: Int = 0): List<JikanSearchResult> {
        val safeLimit = limit.coerceIn(1, KITSU_MAX_PAGE_LIMIT)
        val safeOffset = offset.coerceAtLeast(0)
        return fetchMangaList("/manga?sort=-createdAt&page[limit]=$safeLimit&page[offset]=$safeOffset")
    }

    /** Kitsu'nun subtype filtresiyle ayrı Manhwa ve Manhua kataloğu. */
    suspend fun manhwaManhua(limit: Int = 20, offset: Int = 0): List<JikanSearchResult> {
        val safeLimit = limit.coerceIn(1, KITSU_MAX_PAGE_LIMIT)
        val safeOffset = offset.coerceAtLeast(0)
        return fetchMangaList(
            "/manga?filter[subtype]=manhwa,manhua&sort=-userCount&page[limit]=$safeLimit&page[offset]=$safeOffset"
        )
    }

    /** Kitsu'nun subtype filtresiyle light novel / roman rafı. */
    suspend fun novelsShelf(limit: Int = 20, offset: Int = 0): List<JikanSearchResult> {
        val safeLimit = limit.coerceIn(1, KITSU_MAX_PAGE_LIMIT)
        val safeOffset = offset.coerceAtLeast(0)
        return fetchMangaList(
            "/manga?filter[subtype]=novel&sort=-userCount&page[limit]=$safeLimit&page[offset]=$safeOffset"
        )
    }

    // ── Internal HTTP ─────────────────────────────────────────────────────────

    private suspend fun fetchAnimeList(pathAndQuery: String): List<JikanSearchResult> =
        fetchPaged(pathAndQuery, MediaType.Anime)

    private suspend fun fetchMangaList(pathAndQuery: String): List<JikanSearchResult> =
        fetchPaged(pathAndQuery, MediaType.Manga)

    /**
     * Kitsu liste ucu çağrısı: host seçimi, Typesense sayfa kayması telafisi ve sayfa bekçisi.
     *
     * KÖK NEDEN (Typesense 1-tabanlı sayfa hatası):
     * Kitsu sunucusu (`hummingbird-me/kitsu-server`) `filter[...]` içeren `/anime` ve `/manga`
     * sorgularını `TypesenseSearchService`'e yönlendirir. Sunucudaki `_page = (@_offset / @_limit).floor`
     * hesabı `+ 1` eklemediği için:
     *  - `page[offset]=0&page[limit]=20`  → Typesense `page=0` (1. sayfa döner)
     *  - `page[offset]=20&page[limit]=20` → Typesense `page=1` (YİNE 1. sayfa döner!)
     *  - `page[offset]=40&page[limit]=20` → Typesense `page=2` (gerçek 2. sayfa döner)
     *
     * Bu nedenle `filter[...]` içeren sorgularda `offset > 0` için efektif offset `offset + limit`
     * olarak gönderilir. Filtresiz sorgular (`topAnime`, `topRatedAnime` vb.) doğrudan PostgreSQL
     * `OFFSET` kullandığı için olduğu gibi gönderilir; yine de ilk sayfayla aynı baş kayıt
     * dönerse aynı host üzerinde `offset + limit` ile bir kez daha denenir.
     */
    private suspend fun fetchPaged(
        pathAndQuery: String,
        mediaType: MediaType
    ): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        val offset = pagingOffset(pathAndQuery)
        val limit = pagingLimit(pathAndQuery)
        val anchorKey = pagingAnchorKey(pathAndQuery)
        val effectiveQuery = adjustKitsuTypesenseQuery(pathAndQuery)

        for (host in KitsuApiHost.candidates()) {
            var items = runCatching { fetchFromHost(KitsuApiHost.url(effectiveQuery, host), mediaType) }
                .getOrElse { err ->
                    Log.w(TAG, "Kitsu isteği başarısız ($host): ${err.message}")
                    null
                }
            if (items == null) continue

            var head = items.firstOrNull()?.let { "${it.source}:${it.malId}:${it.title}" }
            if (offset > 0) {
                val anchor = pageAnchors[anchorKey]
                if (anchor != null && head == anchor) {
                    // Eğer henüz offset+limit kaydırması uygulanmadıysa aynı host'ta kaydırarak dene
                    if (effectiveQuery == pathAndQuery) {
                        val shiftedQuery = withPagingOffset(pathAndQuery, offset + limit)
                        val retryItems = runCatching { fetchFromHost(KitsuApiHost.url(shiftedQuery, host), mediaType) }
                            .getOrNull()
                        val retryHead = retryItems?.firstOrNull()?.let { "${it.source}:${it.malId}:${it.title}" }
                        if (!retryItems.isNullOrEmpty() && retryHead != anchor) {
                            return@withContext retryItems
                        }
                    }
                    Log.w(TAG, "Kitsu sayfa bekçisi: offset=$offset aynı kaydı döndürdü ($host atlandı)")
                    continue
                }
            } else if (head != null) {
                pageAnchors[anchorKey] = head
            }
            return@withContext items
        }
        emptyList()
    }

    /**
     * Kitsu'nun `TypesenseSearchService` (`filter[...]` içeren `/anime` ve `/manga` uçları)
     * `(_offset / _limit).floor` hesabındaki 1-tabanlı sayfa kaymasını telafi eder.
     */
    internal fun adjustKitsuTypesenseQuery(pathAndQuery: String): String {
        val offset = pagingOffset(pathAndQuery)
        if (offset <= 0 || !usesTypesensePaging(pathAndQuery)) return pathAndQuery
        val limit = pagingLimit(pathAndQuery)
        return withPagingOffset(pathAndQuery, offset + limit)
    }

    internal fun usesTypesensePaging(pathAndQuery: String): Boolean {
        val path = pathAndQuery.substringBefore('?')
        if (path != "/anime" && path != "/manga") return false
        return pathAndQuery.contains("filter[")
    }

    internal fun withPagingOffset(pathAndQuery: String, newOffset: Int): String =
        if (Regex("page\\[offset\\]=\\d+").containsMatchIn(pathAndQuery)) {
            pathAndQuery.replace(Regex("page\\[offset\\]=\\d+"), "page[offset]=$newOffset")
        } else {
            val sep = if (pathAndQuery.contains('?')) "&" else "?"
            "$pathAndQuery${sep}page[offset]=$newOffset"
        }

    /** Sorgudaki `page[offset]` değeri (yoksa 0). */
    internal fun pagingOffset(pathAndQuery: String): Int =
        Regex("page\\[offset\\]=(\\d+)").find(pathAndQuery)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    /** Sorgudaki `page[limit]` değeri (yoksa 20). */
    internal fun pagingLimit(pathAndQuery: String): Int =
        Regex("page\\[limit\\]=(\\d+)").find(pathAndQuery)?.groupValues?.get(1)?.toIntOrNull()
            ?.coerceIn(1, KITSU_MAX_PAGE_LIMIT)
            ?: KITSU_MAX_PAGE_LIMIT

    /** `page[offset]` hariç kanonik sorgu anahtarı — parametre sırasından bağımsızdır. */
    internal fun pagingAnchorKey(pathAndQuery: String): String {
        val path = pathAndQuery.substringBefore('?')
        val query = pathAndQuery.substringAfter('?', "")
        if (query.isBlank()) return path
        val normalizedParams = query
            .split('&')
            .filter { it.isNotBlank() && !it.startsWith("page[offset]=") }
            .sorted()
        return if (normalizedParams.isEmpty()) path else "$path?${normalizedParams.joinToString("&")}"
    }

    /** Tek bir adresten liste çeker; HTTP hatası/bozuk gövde için `null` döner. */
    private fun fetchFromHost(url: String, mediaType: MediaType): List<JikanSearchResult>? {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.api+json")
            .header("Content-Type", "application/vnd.api+json")
            .header("User-Agent", "Kitsugi/1.0 (Android)")
            .build()

        return try {
            KitsugiHttpClient.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "HTTP ${response.code} for $url")
                    return null
                }
                val body = response.body?.string() ?: return null
                val root = JSONObject(body)
                if (root.has("errors")) {
                    Log.w(TAG, "Kitsu hata yanıtı for $url: ${root.optJSONArray("errors")?.optJSONObject(0)?.optString("detail")}")
                    return null
                }
                val dataArr = root.optJSONArray("data") ?: return emptyList()

                val results = mutableListOf<JikanSearchResult>()
                for (i in 0 until dataArr.length()) {
                    val item = dataArr.optJSONObject(i) ?: continue
                    parseItem(item, mediaType)?.let { results.add(it) }
                }
                results
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Kitsu list from $url: ${e.message}", e)
            null
        }
    }

    private fun parseItem(data: JSONObject, mediaType: MediaType): JikanSearchResult? {
        return try {
            val kitsuNumericId = data.optString("id", "0").toIntOrNull() ?: return null
            val stableId = KITSU_ID_OFFSET + kitsuNumericId

            val attrs = data.optJSONObject("attributes") ?: return null

            val canonicalTitle = attrs.optString("canonicalTitle", "").takeIf { it.isNotBlank() }
            val titlesObj = attrs.optJSONObject("titles")
            val titleEn = titlesObj?.optString("en", "")?.takeIf { it.isNotBlank() }
            val titleEnJp = titlesObj?.optString("en_jp", "")?.takeIf { it.isNotBlank() }
            val titleJa = titlesObj?.optString("ja_jp", "")?.takeIf { it.isNotBlank() }
            val title = canonicalTitle ?: titleEn ?: titleEnJp ?: titleJa ?: return null

            val synopsis = attrs.optString("synopsis", "").takeIf { it.isNotBlank() }

            val startDate = attrs.optString("startDate", "")
            val year = startDate.take(4).toIntOrNull()?.takeIf { it > 1900 }

            val statusRaw = attrs.optString("status", "")
            val statusTr = when (statusRaw.lowercase()) {
                "current"    -> "Yayında"
                "finished"   -> "Tamamlandı"
                "upcoming"   -> "Yakında"
                "unreleased" -> "Yayınlanmadı"
                "tba"        -> "Bilinmiyor"
                else         -> statusRaw
            }

            val subtypeRaw = attrs.optString("subtype", "")
            val subtypeTr = when (subtypeRaw.lowercase()) {
                "tv"      -> "TV"
                "movie"   -> "Film"
                "ova"     -> "OVA"
                "ona"     -> "ONA"
                "special" -> "Özel"
                "music"   -> "Müzik"
                "manga"   -> "Manga"
                "manhwa"  -> "Manhwa"
                "manhua"  -> "Manhua"
                "novel"   -> "Novel"
                "oneshot" -> "One-Shot"
                else      -> subtypeRaw
            }

            val avgRating = attrs.optString("averageRating", "0").toDoubleOrNull() ?: 0.0
            val score = if (avgRating > 0) (avgRating / 10.0).toInt().coerceIn(1, 10) else null

            val userCount = attrs.optInt("userCount", 0).takeIf { it > 0 }
            val favCount  = attrs.optInt("favoritesCount", 0).takeIf { it > 0 }

            val posterObj = attrs.optJSONObject("posterImage")
            val imageUrl  = posterObj?.optString("large")?.takeIf { it.isNotBlank() }
                ?: posterObj?.optString("original")?.takeIf { it.isNotBlank() }
                ?: posterObj?.optString("medium")?.takeIf { it.isNotBlank() }

            val coverObj    = attrs.optJSONObject("coverImage")
            val backdropUrl = coverObj?.optString("original")?.takeIf { it.isNotBlank() }
                ?: coverObj?.optString("large")?.takeIf { it.isNotBlank() }

            val total = when (mediaType) {
                MediaType.Anime, MediaType.Movie, MediaType.TvShow ->
                    attrs.optInt("episodeCount", 0).takeIf { it > 0 }
                MediaType.Manga ->
                    attrs.optInt("chapterCount", 0).takeIf { it > 0 }
            }

            val isAdult   = KitsuAdultFlags.isAdult(attrs)

            // Altyazı: tür + yıl + durum
            val subtitleParts = buildList {
                if (subtypeTr.isNotBlank()) add(subtypeTr)
                if (year != null) add(year.toString())
                if (statusTr.isNotBlank()) add(statusTr)
            }
            val subtitle = if (subtitleParts.isNotEmpty()) subtitleParts.joinToString(" • ")
                           else KitsugiStrings.get(R.string.kitsu_ile_eklenen_anime)

            JikanSearchResult(
                malId         = stableId,
                title         = title,
                subtitle      = subtitle,
                type          = mediaType,
                total         = total,
                score         = score,
                isAdult       = isAdult,
                imageUrl      = imageUrl,
                year          = year,
                source        = "kitsu",
                realMalId     = null,       // Kitsu ek API çağrısı olmadan MAL ID'sini bilmiyor
                titleEnglish  = titleEn,
                titleJapanese = titleJa,
                titleRomaji   = titleEnJp,
                backdropUrl   = backdropUrl,
                members       = userCount,
                favorites     = favCount
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Kitsu item: ${e.message}", e)
            null
        }
    }

    // ── Detail yardımcısı (KitsugiDetailClient tarafından çağrılır) ───────────

    /**
     * stableId veya doğrudan Kitsu numeric ID'den detay çeker.
     * stableId = kitsuId + 300_000_000 veya doğrudan kitsuId
     */
    suspend fun fetchDetailByStableId(stableId: Int, mediaType: MediaType): KitsugiMediaDetail? {
        // stableId her zaman offset'li Kitsu aralığında (300M..399M) olmalı. Aralığın altındaki
        // değerler MAL/AniList kimlik alanlarıyla çakışır; "ham Kitsu ID" diye yorumlanırsa
        // alakasız bir yapımın verisi döner (Listem → Kitsu ayrıntı sayfası hatasının kökü).
        val kitsuNumericId = KitsuIdNamespace.rawIdFromStable(stableId)
        if (kitsuNumericId == null || kitsuNumericId <= 0) {
            Log.w(TAG, "fetchDetailByStableId: $stableId Kitsu kimlik aralığında değil, reddedildi")
            return null
        }
        return when (mediaType) {
            MediaType.Anime, MediaType.Movie, MediaType.TvShow ->
                KitsuClient.fetchAnimeDetail(kitsuNumericId.toString())
            MediaType.Manga ->
                fetchMangaDetail(kitsuNumericId.toString())
        }
    }

    private suspend fun fetchMangaDetail(kitsuId: String): KitsugiMediaDetail? {
        // R18/+18 Kitsu kayıtları anonim isteklere gizlenir (resmî API kuralı). Kayıt
        // kullanıcının listesinden geldiği için oturum varsa jetonla çekiyoruz; jeton
        // yoksa/geçersizse eskisi gibi anonim istek yapılır (bkz. KitsuClient.executeGet).
        val authToken = runCatching { KitsuClient.authTokenOrNull() }.getOrNull()
        return fetchMangaDetailOnce(kitsuId, authToken)
            ?: if (authToken != null) fetchMangaDetailOnce(kitsuId, null) else null
    }

    private suspend fun fetchMangaDetailOnce(kitsuId: String, authToken: String?): KitsugiMediaDetail? =
        withContext(Dispatchers.IO) {
            try {
                // Manga yapım ilişkisi (`mangaProductions`) da istenir: yayınevi ve dergi
                // bilgisi yalnızca bu ilişkiden gelir. Uç reddederse (400) ilişkisiz
                // isteğe düşülür — sayfa hiç açılmaz hâle gelmesin.
                fun load(include: String): KitsugiMediaDetail? {
                    val url = KitsuApiHost.url("/manga/$kitsuId?include=$include")
                    val builder = Request.Builder()
                        .url(url)
                        .header("Accept", "application/vnd.api+json")
                        .header("Content-Type", "application/vnd.api+json")
                        .header("User-Agent", "Kitsugi/1.0 (Android)")
                    if (authToken != null) builder.header("Authorization", "Bearer $authToken")
                    return KitsugiHttpClient.client.newCall(builder.build()).execute().use { response ->
                        if (!response.isSuccessful) return null
                        val body = response.body?.string() ?: return null
                        val root = JSONObject(body)
                        val dataObj = root.optJSONObject("data") ?: return null
                        val includedArr = root.optJSONArray("included")
                        parseMangaDetail(dataObj, includedArr)
                    }
                }

                load("categories,mangaProductions.producer") ?: load("categories")
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching Kitsu manga detail: ${e.message}", e)
                null
            }
        }

    private fun parseMangaDetail(data: JSONObject, included: JSONArray? = null): KitsugiMediaDetail? {
        return try {
            val attrs = data.optJSONObject("attributes") ?: return null
            val (kitsuGenres, kitsuTags) = KitsuClient.parseKitsuCategories(included)
            val (kitsuStudios, kitsuProducers) = KitsuClient.parseKitsuProductions(included)
            val canonicalTitle = attrs.optString("canonicalTitle", "")
            val titlesObj = attrs.optJSONObject("titles")
            val titleEn = titlesObj?.optString("en", "")?.takeIf { it.isNotBlank() }
            val titleJa = titlesObj?.optString("ja_jp", "")?.takeIf { it.isNotBlank() }
            val title = canonicalTitle.takeIf { it.isNotBlank() } ?: titleEn ?: titleJa ?: KitsugiStrings.get(R.string.basliksiz)

            val synopsis = attrs.optString("synopsis", "").takeIf { it.isNotBlank() }
            val startDate = attrs.optString("startDate", "")
            val endDate = attrs.optString("endDate", "")
            val year = startDate.take(4).toIntOrNull()

            val chapterCount = attrs.optInt("chapterCount", 0).takeIf { it > 0 }
            val avgRating = attrs.optString("averageRating", "0").toDoubleOrNull() ?: 0.0
            val score = if (avgRating > 0) (avgRating / 10.0).toInt().coerceIn(1, 10) else null

            val posterObj = attrs.optJSONObject("posterImage")
            val imageUrl = posterObj?.optString("medium")?.takeIf { it.isNotBlank() }
                ?: posterObj?.optString("original")?.takeIf { it.isNotBlank() }
            val posterOriginal = posterObj?.optString("original")?.takeIf { it.isNotBlank() }

            val coverObj = attrs.optJSONObject("coverImage")
            val coverOriginal = coverObj?.optString("original")?.takeIf { it.isNotBlank() }
                ?: coverObj?.optString("large")?.takeIf { it.isNotBlank() }
            val kitsuPictures = listOfNotNull(posterOriginal, coverOriginal).distinct()

            val kitsuId = data.optString("id", "")
            val links = mutableListOf<KitsugiExternalLink>()
            if (kitsuId.isNotBlank()) links.add(KitsugiExternalLink("Kitsu", "https://kitsu.io/manga/$kitsuId", "EN"))

            val statusRaw = attrs.optString("status", "")
            val statusTr = when (statusRaw.lowercase()) {
                "current"    -> "Devam Ediyor"
                "finished"   -> "Tamamlandı"
                "upcoming"   -> "Yakında"
                else         -> statusRaw
            }

            KitsugiMediaDetail(
                synopsis      = synopsis,
                genres        = kitsuGenres,
                tags          = kitsuTags,
                studios       = kitsuStudios,
                producers     = kitsuProducers,
                title         = title,
                titleEnglish  = titleEn,
                titleJapanese = titleJa,
                imageUrl      = imageUrl,
                bannerImage   = coverOriginal,
                type          = MediaType.Manga,
                score         = score,
                year          = year,
                total         = chapterCount,
                startDate     = startDate.takeIf { it.isNotBlank() },
                endDate       = endDate.takeIf { it.isNotBlank() },
                status        = statusTr,
                isAdult       = KitsuAdultFlags.isAdult(attrs),
                externalLinks = links,
                pictures      = kitsuPictures
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Kitsu manga detail: ${e.message}", e)
            null
        }
    }

    /** Kitsu ID offset sabiti — dışarıdan erişilebilir */
    const val ID_OFFSET = KITSU_ID_OFFSET
}
