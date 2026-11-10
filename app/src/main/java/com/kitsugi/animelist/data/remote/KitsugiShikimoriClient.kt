package com.kitsugi.animelist.data.remote

import android.util.Log
import com.kitsugi.animelist.model.MediaType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import com.kitsugi.animelist.utils.*
import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.R

object KitsugiShikimoriClient {
    private const val TAG = "KitsugiShikimoriClient"
    // Kanonik host: shikimori.one/.me/.org artık shikimori.io'ya yönleniyor
    // (bkz. ShikimoriApiClient.BASE_URL ve ShikimoriPosterResolver.HOST).
    private const val BASE_URL = "https://shikimori.io/api"

    /**
     * Metin aramasında varsayılan anime türleri. Shikimori arama sonuçlarına müzik (music),
     * PV (pv) ve reklam (cm) öğelerini de ekliyor; bunlar anime aramasında alakasız görünüyordu
     * ("Na mo Naki Nanimo Kamo", "... PV", "Suntory ... Tennensui" gibi). Kullanıcı tür filtresi
     * seçtiyse o liste geçerlidir.
     */
    private const val ANIME_SEARCH_DEFAULT_KINDS = "tv,movie,ova,ona,special,tv_special"

    /**
     * Detay sayfasının çekirdek verisini (başlık, kapak, puan, özet) bloklamayan ikincil
     * isteklerin (tanıtım videoları, harici linkler, tema müzikleri) süre tavanı.
     * Bu istekler paralel çalışır; süre dolarsa sayfa onlarsız açılır.
     */
    private const val SHIKI_EXTRAS_TIMEOUT_MS = 3_500L

    /** Özet/biyografi çevirisi için süre tavanı — sayfa çeviriyi beklemez. */
    private const val SHIKI_TRANSLATE_TIMEOUT_MS = 3_000L

    /** Kiril alfabesindeki karakter/kişi adlarının toplu çevirisi için toplam bütçe. */
    private const val SHIKI_NAME_TRANSLATE_BUDGET_MS = 3_500L

    /** Ad çevirisinde eşzamanlı istek sayısı (Google'ı boğmadan hız). */
    private const val SHIKI_NAME_TRANSLATE_CONCURRENCY = 6

    private fun absoluteStudioImageUrl(value: String?): String? {
        val path = value?.takeIf { it.isNotBlank() && it != "null" } ?: return null
        return when {
            path.startsWith("https://", ignoreCase = true) || path.startsWith("http://", ignoreCase = true) -> path
            path.startsWith("//") -> "https:$path"
            path.startsWith("/") -> "https://shikimori.io$path"
            else -> null
        }
    }

    private fun adultFlag(item: JSONObject): Boolean {
        val rating = item.optString("rating").takeIf { it.isNotBlank() && it != "null" }
        val kind = item.optString("kind", "")
        val genres = item.optJSONArray("genres")?.let { array ->
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.optString("name")
                    ?.takeIf { it.isNotBlank() && it != "null" }
            }
        }.orEmpty()
        return isShikimoriAdultContent(rating, genres) || kind.equals("hentai", ignoreCase = true)
    }

    // ─── Arama fonksiyonları ───────────────────────────────────────────────

    /**
     * Shikimori REST API üzerinden anime araması.
     * MAL/Jikan sunucusu hata verdiğinde fallback olarak kullanılır.
     */
    suspend fun searchAnime(query: String, limit: Int = 20): List<JikanSearchResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val encoded = java.net.URLEncoder.encode(query, "UTF-8")
                val url = URL(
                    "$BASE_URL/animes?search=$encoded&limit=$limit&order=popularity" +
                        "&censored=true&kind=$ANIME_SEARCH_DEFAULT_KINDS"
                )
                Log.d(TAG, "Shikimori searchAnime: $url")
                val response = KitsugiApiBase.executeGetRequestResilient(url) ?: return@runCatching emptyList()
                val array = JSONArray(response)
                val results = mutableListOf<JikanSearchResult>()
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val id = item.optInt("id")
                    if (id <= 0) continue
                    val romajiTitle = item.optString("name", "").trim()
                    val russianTitle = item.optString("russian", "").trim()
                    val title = romajiTitle.ifBlank { russianTitle }
                    val relativeImg = item.optJSONObject("image")?.optString("original")
                    val imageUrl = ShikimoriPosterResolver.absoluteUrl(relativeImg)
                    val kind = item.optString("kind", "tv")
                    val score = item.optString("score", "0").toDoubleOrNull()?.toInt()?.coerceIn(0, 10)
                    val year = item.optString("aired_on", "").take(4).toIntOrNull()
                    val episodes = item.optInt("episodes").takeIf { it > 0 }
                    val mediaType = when (kind) {
                        "movie" -> com.kitsugi.animelist.model.MediaType.Movie
                        "manga", "novel", "manhwa", "manhua", "one_shot", "doujin" -> com.kitsugi.animelist.model.MediaType.Manga
                        else -> com.kitsugi.animelist.model.MediaType.Anime
                    }
                    results.add(
                        JikanSearchResult(
                            malId = id,
                            title = title,
                            subtitle = kind.uppercase(),
                            type = mediaType,
                            total = episodes,
                            score = score,
                            isAdult = adultFlag(item),
                            imageUrl = imageUrl,
                            year = year,
                            source = "shikimori",
                            realMalId = item.optInt("myanimelist_id").takeIf { it in 1..99_999_999 },
                            titleRomaji = romajiTitle.ifBlank { null }
                        )
                    )
                }
                withResolvedMediaMetadata(ShikimoriPosterResolver.Kind.ANIME, results)
            }.getOrElse { err ->
                Log.e(TAG, "Shikimori searchAnime exception: ${err.message}", err)
                emptyList()
            }
        }

    /**
     * Shikimori REST API üzerinden manga araması.
     */
    suspend fun searchManga(query: String, limit: Int = 20): List<JikanSearchResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val encoded = java.net.URLEncoder.encode(query, "UTF-8")
                val url = URL("$BASE_URL/mangas?search=$encoded&limit=$limit&order=popularity")
                Log.d(TAG, "Shikimori searchManga: $url")
                val response = KitsugiApiBase.executeGetRequestResilient(url) ?: return@runCatching emptyList()
                val array = JSONArray(response)
                val results = mutableListOf<JikanSearchResult>()
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val id = item.optInt("id")
                    if (id <= 0) continue
                    val romajiTitle = item.optString("name", "").trim()
                    val russianTitle = item.optString("russian", "").trim()
                    val title = romajiTitle.ifBlank { russianTitle }
                    val relativeImg = item.optJSONObject("image")?.optString("original")
                    val imageUrl = ShikimoriPosterResolver.absoluteUrl(relativeImg)
                    val kind = item.optString("kind", "manga")
                    val score = item.optString("score", "0").toDoubleOrNull()?.toInt()?.coerceIn(0, 10)
                    val year = item.optString("aired_on", "").take(4).toIntOrNull()
                    val chapters = item.optInt("chapters").takeIf { it > 0 }
                    results.add(
                        JikanSearchResult(
                            malId = id,
                            title = title,
                            subtitle = kind.uppercase(),
                            type = com.kitsugi.animelist.model.MediaType.Manga,
                            total = chapters,
                            score = score,
                            isAdult = adultFlag(item),
                            imageUrl = imageUrl,
                            year = year,
                            source = "shikimori",
                            realMalId = item.optInt("myanimelist_id").takeIf { it in 1..99_999_999 },
                            titleRomaji = romajiTitle.ifBlank { null }
                        )
                    )
                }
                withResolvedMediaMetadata(ShikimoriPosterResolver.Kind.MANGA, results)
            }.getOrElse { err ->
                Log.e(TAG, "Shikimori searchManga exception: ${err.message}", err)
                emptyList()
            }
        }

    suspend fun searchMediaAdvanced(
        mediaType: com.kitsugi.animelist.model.MediaType,
        query: String = "",
        page: Int = 1,
        limit: Int = 24,
        order: String = "popularity",
        kinds: List<String>? = null,
        statuses: List<String>? = null,
        season: String? = null,
        score: Int? = null,
        duration: String? = null,
        rating: String? = null,
        genres: List<Int>? = null,
        excludedGenres: List<Int>? = null,
        studioId: Int? = null,
        publisherId: Int? = null,
        censored: Boolean = true
    ): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = when (mediaType) {
                com.kitsugi.animelist.model.MediaType.Manga -> "mangas"
                else -> "animes"
            }
            val params = mutableListOf<String>()
            params.add("limit=$limit")
            params.add("page=$page")
            params.add("order=$order")
            params.add("censored=$censored")

            if (query.isNotBlank()) params.add("search=${java.net.URLEncoder.encode(query.trim(), "UTF-8")}")
            if (!kinds.isNullOrEmpty()) {
                params.add("kind=${kinds.joinToString(",")}")
            } else if (query.isNotBlank() && endpoint == "animes") {
                // Metin araması ve kullanıcı tür seçmemiş: müzik/PV/reklam öğelerini dışla.
                params.add("kind=$ANIME_SEARCH_DEFAULT_KINDS")
            }
            if (!statuses.isNullOrEmpty()) params.add("status=${statuses.joinToString(",")}")
            if (!season.isNullOrBlank()) params.add("season=$season")
            if (score != null && score > 0) params.add("score=$score")
            if (!duration.isNullOrBlank()) params.add("duration=$duration")
            if (!rating.isNullOrBlank()) params.add("rating=$rating")
            if (studioId != null && studioId > 0) params.add("studio=$studioId")
            if (publisherId != null && publisherId > 0) params.add("publisher=$publisherId")

            val genreParts = mutableListOf<String>()
            genres?.forEach { genreParts.add("$it") }
            excludedGenres?.forEach { genreParts.add("!$it") }
            if (genreParts.isNotEmpty()) {
                params.add("genre=${genreParts.joinToString(",")}")
            }

            val url = URL("$BASE_URL/$endpoint?${params.joinToString("&")}")
            val response = KitsugiApiBase.executeGetRequestResilient(url) ?: return@runCatching emptyList()
            val array = JSONArray(response)
            val results = mutableListOf<JikanSearchResult>()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optInt("id")
                if (id <= 0) continue
                val romajiTitle = item.optString("name", "").trim()
                val russianTitle = item.optString("russian", "").trim()
                val title = romajiTitle.ifBlank { russianTitle }
                val relativeImg = item.optJSONObject("image")?.optString("original")
                val imageUrl = ShikimoriPosterResolver.absoluteUrl(relativeImg)
                val kind = item.optString("kind", "tv")
                val sc = item.optString("score", "0").toDoubleOrNull()?.toInt()?.coerceIn(0, 10)
                val yr = item.optString("aired_on", "").take(4).toIntOrNull()
                val episodes = item.optInt("episodes").takeIf { it > 0 }
                val epOrCh = if (mediaType == com.kitsugi.animelist.model.MediaType.Manga) item.optInt("chapters").takeIf { it > 0 } else episodes
                val isAdult = adultFlag(item)
                results.add(
                    JikanSearchResult(
                        malId = id,
                        title = title,
                        subtitle = kind.uppercase(),
                        type = mediaType,
                        total = epOrCh,
                        score = sc,
                        isAdult = isAdult,
                        imageUrl = imageUrl,
                        year = yr,
                        source = "shikimori",
                        realMalId = item.optInt("myanimelist_id").takeIf { it in 1..99_999_999 },
                        titleRomaji = romajiTitle.ifBlank { null }
                    )
                )
            }
            withResolvedMediaMetadata(
                if (mediaType == com.kitsugi.animelist.model.MediaType.Manga) ShikimoriPosterResolver.Kind.MANGA
                else ShikimoriPosterResolver.Kind.ANIME,
                results
            )
        }.getOrElse { emptyList() }
    }

    /** Shikimori Popüler / Trend Animeler */
    suspend fun trendingAnime(limit: Int = 20, page: Int = 1, censored: Boolean = true): List<JikanSearchResult> =
        searchMediaAdvanced(com.kitsugi.animelist.model.MediaType.Anime, order = "popularity", limit = limit, page = page, censored = censored)

    /** Shikimori Sezonluk Animeler (örn: "spring_2025", "winter_2025") */
    suspend fun seasonalAnime(season: String, limit: Int = 20, page: Int = 1, censored: Boolean = true): List<JikanSearchResult> =
        searchMediaAdvanced(com.kitsugi.animelist.model.MediaType.Anime, season = season, order = "popularity", limit = limit, page = page, censored = censored)

    /** Shikimori Film Formatındaki Animeler */
    suspend fun movieAnime(limit: Int = 20, page: Int = 1, censored: Boolean = true): List<JikanSearchResult> =
        searchMediaAdvanced(com.kitsugi.animelist.model.MediaType.Anime, kinds = listOf("movie"), order = "popularity", limit = limit, page = page, censored = censored)

    /** Shikimori En Yüksek Puanlı Animeler */
    suspend fun topAnime(limit: Int = 20, page: Int = 1, censored: Boolean = true): List<JikanSearchResult> =
        searchMediaAdvanced(com.kitsugi.animelist.model.MediaType.Anime, order = "ranked", limit = limit, page = page, censored = censored)

    /** Shikimori Yayında Olan Animeler */
    suspend fun airingAnime(limit: Int = 20, page: Int = 1, censored: Boolean = true): List<JikanSearchResult> =
        searchMediaAdvanced(com.kitsugi.animelist.model.MediaType.Anime, statuses = listOf("ongoing"), order = "popularity", limit = limit, page = page, censored = censored)

    /** Shikimori En Popüler Mangalar */
    suspend fun topManga(limit: Int = 20, page: Int = 1, censored: Boolean = true): List<JikanSearchResult> =
        searchMediaAdvanced(com.kitsugi.animelist.model.MediaType.Manga, order = "ranked", limit = limit, page = page, censored = censored)

    /** Shikimori Yayında Olan Mangalar */
    suspend fun publishingManga(limit: Int = 20, page: Int = 1, censored: Boolean = true): List<JikanSearchResult> =
        searchMediaAdvanced(com.kitsugi.animelist.model.MediaType.Manga, statuses = listOf("ongoing"), order = "popularity", limit = limit, page = page, censored = censored)

    suspend fun searchCharacters(query: String, page: Int = 1, limit: Int = 24): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        runCatching {
            val encoded = java.net.URLEncoder.encode(query.trim(), "UTF-8")
            val url = URL("$BASE_URL/characters/search?search=$encoded&page=$page&limit=$limit")
            val response = KitsugiApiBase.executeGetRequestResilient(url) ?: return@runCatching emptyList()
            val array = JSONArray(response)
            val results = mutableListOf<JikanSearchResult>()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optInt("id")
                if (id <= 0) continue
                val name = item.optString("name", "").ifBlank { item.optString("russian", "Karakter") }
                val relativeImg = item.optJSONObject("image")?.optString("original")
                val imageUrl = ShikimoriPosterResolver.absoluteUrl(relativeImg)
                results.add(
                    JikanSearchResult(
                        malId = id,
                        title = name,
                        subtitle = "Karakter (Shikimori)",
                        type = com.kitsugi.animelist.model.MediaType.Anime,
                        total = null,
                        score = null,
                        isAdult = false,
                        imageUrl = imageUrl,
                        year = null,
                        source = "shikimori"
                    )
                )
            }
            withResolvedPosters(ShikimoriPosterResolver.Kind.CHARACTER, results)
        }.getOrElse { emptyList() }
    }

    suspend fun searchPeople(query: String, page: Int = 1, limit: Int = 24, kind: String? = null): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        runCatching {
            val encoded = java.net.URLEncoder.encode(query.trim(), "UTF-8")
            val kindParam = if (!kind.isNullOrBlank()) "&kind=$kind" else ""
            val url = URL("$BASE_URL/people/search?search=$encoded&page=$page&limit=$limit$kindParam")
            val response = KitsugiApiBase.executeGetRequestResilient(url) ?: return@runCatching emptyList()
            val array = JSONArray(response)
            val results = mutableListOf<JikanSearchResult>()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optInt("id")
                if (id <= 0) continue
                val name = item.optString("name", "").ifBlank { item.optString("russian", KitsugiStrings.get(R.string.gallery_cat_person)) }
                val relativeImg = item.optJSONObject("image")?.optString("original")
                val imageUrl = ShikimoriPosterResolver.absoluteUrl(relativeImg)
                val role = when (kind) {
                    "seyu" -> "Seiyuu / Seslendirmen"
                    "mangaka" -> "Mangaka / Yazar"
                    "producer" -> "Yapımcı / Yönetmen"
                    else -> "Kişi / Ekip"
                }
                results.add(
                    JikanSearchResult(
                        malId = id,
                        title = name,
                        subtitle = "$role (Shikimori)",
                        type = com.kitsugi.animelist.model.MediaType.Anime,
                        total = null,
                        score = null,
                        isAdult = false,
                        imageUrl = imageUrl,
                        year = null,
                        source = "shikimori"
                    )
                )
            }
            withResolvedPosters(ShikimoriPosterResolver.Kind.PERSON, results)
        }.getOrElse { emptyList() }
    }

    /**
     * Shikimori REST API üzerinden bir animenin ekran görüntülerini (screenshots / backdrops) çeker.
     * Her görsel orijinal tam çözünürlüktedir.
     */
    suspend fun fetchScreenshots(animeId: Int): List<GalleryItem> = withContext(Dispatchers.IO) {
        if (animeId <= 0) return@withContext emptyList()
        val cached = DetailCache.getShikimoriGallery(animeId)
        if (cached != null) return@withContext cached

        val url = URL("$BASE_URL/animes/$animeId/screenshots")
        val response = KitsugiApiBase.executeGetRequestResilient(url) ?: return@withContext emptyList()
        runCatching {
            val array = JSONArray(response)
            val list = mutableListOf<GalleryItem>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val relOriginal = obj.optString("original", "").trim()
                if (relOriginal.isBlank()) continue
                val fullUrl = ShikimoriPosterResolver.absoluteUrl(relOriginal) ?: continue
                list.add(
                    GalleryItem(
                        url = fullUrl,
                        source = "Shikimori",
                        category = GalleryCategory.BACKDROP,
                        description = KitsugiStrings.get(R.string.shikimori_ekran_goruntusu),
                        language = "ru"
                    )
                )
            }
            if (list.isNotEmpty()) {
                DetailCache.putShikimoriGallery(animeId, list)
            }
            list
        }.getOrElse {
            Log.e(TAG, "Shikimori fetchScreenshots exception: ${it.message}", it)
            emptyList()
        }
    }

    /**
     * Shikimori REST API üzerinden anime veya manga detaylarını çeker.
     * Yaş sınırını ("rating") doğru şekilde ayrıştırır ve yalnızca "rx" (Hentai)
     * olanları +18 (isAdult) olarak işaretler; "r" (17+ şiddet/aksiyon) içerikleri
     * asla yetişkin olarak blurlamaz.
     */
    suspend fun fetchDetail(externalId: Int, mediaType: MediaType): KitsugiMediaDetail? =
        withContext(Dispatchers.IO) {
            if (externalId <= 0) return@withContext null
            val endpoint = when (mediaType) {
                MediaType.Anime, MediaType.Movie, MediaType.TvShow -> "animes"
                MediaType.Manga -> "mangas"
            }
            val url = URL("$BASE_URL/$endpoint/$externalId")
            Log.d(TAG, "Shikimori fetchDetail: $url")
            runCatching {
                KitsugiApiBase.runWithRateLimit {
                    val response = KitsugiApiBase.executeGetRequestResilient(url) ?: return@runWithRateLimit null
                    val data = JSONObject(response)
                    val metadata = ShikimoriDetailParser.parse(data, mediaType)

                    val russianTitle = metadata.russianTitle
                    val romajiTitle = data.optNullableString("name")
                    val engTitle = metadata.englishTitle
                    val japTitle = metadata.japaneseTitle
                    val mainTitle = romajiTitle ?: engTitle ?: russianTitle ?: "Bilinmeyen"

                    val relativeImg = data.optJSONObject("image")?.optString("original")
                    // REST `image` yer tutucuya düşerse gerçek kapak GraphQL'den alınır.
                    val imageUrl = ShikimoriPosterResolver.bestImageUrl(
                        kind = if (mediaType == MediaType.Manga) ShikimoriPosterResolver.Kind.MANGA
                        else ShikimoriPosterResolver.Kind.ANIME,
                        id = externalId,
                        restImageUrl = relativeImg,
                        preferFull = true
                    )

                    val rawScore = data.optString("score", "0").toDoubleOrNull()
                    val score = rawScore?.toInt()?.coerceIn(0, 10)
                    val meanScore = rawScore?.let { (it * 10).toInt() }

                    val year = data.optString("aired_on", "").take(4).toIntOrNull()
                    val total = if (mediaType == MediaType.Manga) {
                        data.optInt("chapters").takeIf { it > 0 }
                    } else {
                        data.optInt("episodes").takeIf { it > 0 }
                    }

                    val ratingRaw = data.optString("rating", "").lowercase()
                    val statusStr = metadata.status?.toTurkishStatus()

                    val genresList = mutableListOf<String>()
                    // `kind` alanı Shikimori'nin kendi sınıflandırmasıdır: theme/demographic
                    // ayrımı korunur (birleştirme ve filtre bu alanları ayrı kullanır).
                    val shikiThemes = mutableListOf<String>()
                    val shikiDemographics = mutableListOf<String>()
                    val shikiTags = mutableListOf<KitsugiTag>()
                    val genresArr = data.optJSONArray("genres")
                    if (genresArr != null) {
                        for (i in 0 until genresArr.length()) {
                            val gObj = genresArr.optJSONObject(i) ?: continue
                            val gName = gObj.optString("name")
                            val gKind = gObj.optString("kind", "").lowercase()
                            if (gName.isNotBlank()) {
                                genresList.add(gName)
                                when (gKind) {
                                    "theme" -> shikiThemes.add(gName)
                                    "demographic" -> shikiDemographics.add(gName)
                                }
                                if (gKind == "theme" || gKind == "demographic" || !com.kitsugi.animelist.ui.screens.search.CanonicalFilterBridge.isOfficialAniListGenre(gName)) {
                                    shikiTags.add(
                                        KitsugiTag(
                                            id = gObj.optInt("id").takeIf { it > 0 },
                                            name = gName,
                                            isSpoiler = KitsugiSpoilerRules.isIntrinsicSpoiler(gName),
                                            source = "shikimori"
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // SADECE "rx" (Hentai) veya hentai türü yetişkin (+18) olarak kabul edilir!
                    // "r" (17+ şiddet/aksiyon, PG-13 vb.) kesinlikle +18 DEĞİLDİR!
                    val isAdult = isShikimoriAdultContent(ratingRaw, genresList)

                    val rating = when (ratingRaw) {
                        "g"      -> "G - All Ages"
                        "pg"     -> "PG - Children"
                        "pg_13"  -> "PG-13 - Teens 13 or older"
                        "r"      -> "R - 17+ (violence & profanity)"
                        "r_plus" -> "R+ - Mild Nudity"
                        "rx"     -> "Rx - Hentai"
                        else     -> null
                    }?.toTurkishRating()

                    val studiosList = mutableListOf<KitsugiStudio>()
                    val studiosArr = data.optJSONArray("studios")
                    if (studiosArr != null) {
                        for (i in 0 until studiosArr.length()) {
                            val sObj = studiosArr.optJSONObject(i) ?: continue
                            val sId = sObj.optInt("id")
                            val sName = sObj.optString("name")
                            val sImage = sObj.optJSONObject("image")?.let { image ->
                                absoluteStudioImageUrl(image.optString("original").takeIf { it.isNotBlank() && it != "null" }
                                    ?: image.optString("preview").takeIf { it.isNotBlank() && it != "null" })
                            }
                            if (sId > 0 && sName.isNotBlank()) {
                                studiosList.add(KitsugiStudio(id = sId, name = sName, isMain = true, source = "shikimori", imageUrl = sImage))
                            }
                        }
                    }

                    // ── Lisans verenler / yayınevleri ──────────────────────────
                    // Shikimori `licensors` DÜZ STRING dizisidir (bazı kayıtlarda nesne),
                    // `publishers` (manga) ise nesne dizisidir. İkisi de kurumdur; daha önce
                    // hiç okunmuyordu → Shikimori kaynaklı sayfalarda lisans veren/yayıncı
                    // listesi boş görünüyordu. Rusça adlar Latin harflerine çevrilir.
                    val shikiLicensors = parseShikimoriOrgArray(
                        data.optJSONArray("licensors"),
                        StudioRole.LICENSOR
                    )
                    val shikiPublishers = parseShikimoriOrgArray(
                        data.optJSONArray("publishers"),
                        StudioRole.PUBLISHER
                    )
                    val shikiProdOrgs = (shikiLicensors + shikiPublishers)
                        .filter { org -> studiosList.none { it.name.equals(org.name, ignoreCase = true) } }

                    // NOT: Shikimori ID'si MAL ID'si DEĞİLDİR! Eski kod `myanimelist_id`
                    // alanı boşken buraya Shikimori ID'sini yazıyordu; sonuç: Shikimori
                    // kaydı MAL ID sanılıp alakasız yapımların puanı/bölümü/afişi çekiliyordu.
                    val realMalId = data.optInt("myanimelist_id").takeIf { it > 0 }

                    // ── İkincil veriler PARALEL ve ZAMAN TAVANLI ────────────────────────
                    // Eskiden tanıtım videoları, harici linkler ve tema müzikleri sırayla
                    // bekleniyordu (her biri 1-3 sn); detay sayfası bu yüzden 5-15 sn'de
                    // açılıyordu. Artık üçü aynı anda başlar ve en fazla
                    // SHIKI_EXTRAS_TIMEOUT_MS beklenir; gelmeyen bölüm atlanır.
                    val extras = coroutineScope {
                        val videosDef = async(Dispatchers.IO) {
                            if (mediaType == MediaType.Manga) {
                                emptyList<Pair<String, KitsugiTheme>>()
                            } else {
                                runCatching {
                                    withTimeoutOrNull(SHIKI_EXTRAS_TIMEOUT_MS) { fetchShikimoriVideos(externalId) }
                                }.getOrNull().orEmpty()
                            }
                        }
                        val linksDef = async(Dispatchers.IO) {
                            runCatching {
                                withTimeoutOrNull(SHIKI_EXTRAS_TIMEOUT_MS) {
                                    fetchShikimoriExternalLinks(externalId, mediaType)
                                }
                            }.getOrNull().orEmpty()
                        }
                        val themesDef = async(Dispatchers.IO) {
                            val malId = realMalId
                            if (mediaType == MediaType.Manga || malId == null || malId <= 0) {
                                Pair(emptyList<KitsugiTheme>(), emptyList<KitsugiTheme>())
                            } else {
                                runCatching {
                                    withTimeoutOrNull(SHIKI_EXTRAS_TIMEOUT_MS) {
                                        KitsugiAnimeThemesClient.fetchAnimeThemes(malId, "MyAnimeList")
                                    }
                                }.getOrNull() ?: Pair(emptyList<KitsugiTheme>(), emptyList<KitsugiTheme>())
                            }
                        }
                        Triple(videosDef.await(), linksDef.await(), themesDef.await())
                    }

                    // Shikimori ekstra videolar (PV, Teaser, Karakter tanıtımları, OP, ED)
                    val shikiVideos = extras.first

                    // Shikimori harici ve yayın linkleri. Kaydın kendi sayfası ve doğrulanmış
                    // MAL karşılığı da listede bulunur; API aynı URL'yi tekrar ederse tekilleştirilir.
                    val shikimoriPage = ShikimoriPosterResolver.absoluteUrl(
                        data.optNullableString("url") ?: "/$endpoint/$externalId"
                    )?.let { KitsugiExternalLink(site = "Shikimori", url = it) }
                    val malPage = realMalId?.let { malId ->
                        val kind = if (mediaType == MediaType.Manga) "manga" else "anime"
                        KitsugiExternalLink(site = "MyAnimeList", url = "https://myanimelist.net/$kind/$malId")
                    }
                    val shikiLinks = (listOfNotNull(shikimoriPage) + extras.second + listOfNotNull(malPage))
                        .distinctBy { link ->
                            link.url.trim().substringAfter("://").trimEnd('/').lowercase(java.util.Locale.ROOT)
                        }

                    // AnimeThemes entegrasyonu (MyAnimeList ID üzerinden)
                    val (themeOps, themeEds) = extras.third

                    // Fragman (PV)
                    var trailerUrl: String? = null
                    val videosArr = data.optJSONArray("videos")
                    if (videosArr != null) {
                        for (i in 0 until videosArr.length()) {
                            val vObj = videosArr.optJSONObject(i) ?: continue
                            val vUrl = vObj.optString("url")
                            if (vUrl.isNotBlank() && (vUrl.contains("youtube.com") || vUrl.contains("youtu.be"))) {
                                trailerUrl = vUrl
                                break
                            }
                        }
                    }
                    if (trailerUrl.isNullOrBlank()) {
                        trailerUrl = shikiVideos.firstOrNull { it.first == "pv" }?.second?.videoUrl
                            ?: shikiVideos.firstOrNull()?.second?.videoUrl
                    }

                    // Açılış müzikleri & ekstra tanıtım videoları
                    val extraVideosAsThemes = shikiVideos.filter { it.first != "ed" }.map { it.second }
                    val finalOpenings = (themeOps + extraVideosAsThemes).distinctBy { it.videoUrl ?: it.label }

                    // Kapanış müzikleri & ED videoları
                    val extraEdsAsThemes = shikiVideos.filter { it.first == "ed" }.map { it.second }
                    val finalEndings = (themeEds + extraEdsAsThemes).distinctBy { it.videoUrl ?: it.label }

                    val streamingLinks = shikiLinks.filter { ShikimoriDetailParser.isStreamingSite(it.site) }
                    val externalLinks = shikiLinks.filterNot { ShikimoriDetailParser.isStreamingSite(it.site) }

                    val rawDesc = data.optNullableString("description")?.cleanApiText()
                    // Çeviri sayfa açılışını bloklamaz: kısa bütçeyle denenir, olmazsa
                    // özgün (Rusça) metin gösterilir ve arka planda çeviri önbelleğe girer.
                    val synopsis = translateWithBudget(rawDesc)

                    KitsugiMediaDetail(
                        synopsis = synopsis,
                        // Shikimori'nin `kind` alanı türün asıl kaynağıdır: "movie" kaydı
                        // "Anime/TV" olarak etiketlenirse film akışları (TMDB galeri türü,
                        // bölüm sayısı, oynatıcı varsayılanları) yanlış çalışıyordu.
                        type = if (mediaType == MediaType.Manga) MediaType.Manga
                               else kindToMediaType(data.optString("kind", "tv")),
                        genres = genresList.toTurkishGenres(),
                        themes = shikiThemes.distinct().toTurkishGenres(),
                        demographics = shikiDemographics.distinct().toTurkishGenres(),
                        status = statusStr,
                        season = metadata.season?.toTurkishSeason(),
                        studios = studiosList,
                        rating = rating,
                        episodeDuration = data.optInt("duration").takeIf { it > 0 }?.let {
                            KitsugiStrings.get(R.string.detail_info_duration_minutes, it)
                        },
                        startDate = data.optNullableString("aired_on"),
                        endDate = data.optNullableString("released_on"),
                        titleEnglish = engTitle,
                        titleJapanese = japTitle,
                        titleRomaji = romajiTitle,
                        titleNative = japTitle,
                        titleRussian = russianTitle,
                        synonyms = metadata.synonyms,
                        trailerUrl = trailerUrl,
                        title = mainTitle,
                        imageUrl = imageUrl,
                        score = score,
                        meanScore = meanScore,
                        averageScore = meanScore,
                        year = year,
                        total = total,
                        isAdult = isAdult,
                        realMalId = realMalId,
                        tags = shikiTags,
                        producers = shikiProdOrgs,
                        serializations = shikiPublishers,
                        openings = finalOpenings,
                        endings = finalEndings,
                        externalLinks = externalLinks,
                        streamingLinks = streamingLinks,
                        nextAiringEpisode = metadata.nextAiringEpisode,
                        popularity = metadata.members,
                        scoredBy = metadata.scoredBy,
                        members = metadata.members,
                        isFavourite = metadata.isFavourite,
                        format = metadata.format,
                        volumes = metadata.volumes,
                        rawFormat = metadata.rawFormat,
                        rawStatus = metadata.rawStatus,
                        rawSeason = metadata.rawSeason,
                        seasonYear = year,
                        episodesAired = metadata.episodesAired,
                        publishers = metadata.publishers ?: shikiPublishers.map { it.name }.takeIf { it.isNotEmpty() },
                        licensors = metadata.licensors ?: shikiLicensors.map { it.name }.takeIf { it.isNotEmpty() },
                        fansubbers = metadata.fansubbers,
                        fandubbers = metadata.fandubbers,
                        nativeStats = metadata.nativeStats
                    )
                }
            }.getOrElse { err ->
                Log.e(TAG, "Shikimori fetchDetail exception: ${err.message}", err)
                null
            }
        }

    /**
     * Shikimori'nin `licensors` / `publishers` dizisini kurum listesine çevirir.
     *
     * Dizi elemanları **düz string** (`"licensors": ["Crunchyroll"]`) veya **nesne**
     * (`{"id":1,"name":"Kodansha"}`) olabilir; ikisi de desteklenir. Kiril adlar
     * [toLatinStudioName] ile Latin harflerine çevrilir (ekranda Rusça görünmez).
     */
    internal fun parseShikimoriOrgArray(
        arr: org.json.JSONArray?,
        role: StudioRole,
        source: String = "shikimori"
    ): List<KitsugiStudio> {
        if (arr == null || arr.length() == 0) return emptyList()
        val out = ArrayList<KitsugiStudio>(arr.length())
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i)
            val raw = if (item != null) item.optString("name", "") else arr.optString(i, "")
            val name = raw.trim()
            if (name.isEmpty() || name == "null") continue
            val id = item?.let { obj -> obj.optInt("id", 0).takeIf { value -> value > 0 } } ?: 0
            out.add(
                KitsugiStudio(
                    id = id,
                    name = name.toLatinStudioName(),
                    isMain = role == StudioRole.STUDIO,
                    source = source,
                    role = role
                )
            )
        }
        return out.distinctBy { it.name.lowercase(java.util.Locale.ROOT) }
    }

    private val shikiVideosCache = com.kitsugi.animelist.core.memory.BoundedCache<Int, List<Pair<String, KitsugiTheme>>>("shikimori.videos", 120)

    suspend fun fetchShikimoriVideos(animeId: Int): List<Pair<String, KitsugiTheme>> = withContext(Dispatchers.IO) {
        if (animeId <= 0) return@withContext emptyList()
        shikiVideosCache[animeId]?.let { return@withContext it }

        val url = runCatching { URL("$BASE_URL/animes/$animeId/videos") }.getOrNull() ?: return@withContext emptyList()
        val parsed = runCatching {
            val response = KitsugiApiBase.executeGetRequestResilient(url) ?: return@runCatching emptyList()
            val array = JSONArray(response)
            val list = mutableListOf<Pair<String, KitsugiTheme>>()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val directUrl = item.optString("url")
                val playerUrl = item.optString("player_url")
                val ytId = extractCanonicalYouTubeId(directUrl) ?: extractCanonicalYouTubeId(playerUrl)
                val vUrl = when {
                    ytId != null -> "https://www.youtube.com/watch?v=$ytId"
                    directUrl.isNotBlank() && (directUrl.contains("youtube.com") || directUrl.contains("youtu.be")) -> directUrl
                    else -> continue
                }
                val rawName = item.optString("name", "").trim()
                val kind = item.optString("kind", "pv").lowercase(java.util.Locale.ROOT)
                val kindLabel = when (kind) {
                    "pv", "episode_preview" -> KitsugiStrings.get(R.string.source_video_pv)
                    "character_trailer" -> KitsugiStrings.get(R.string.source_video_character_trailer)
                    "op" -> KitsugiStrings.get(R.string.detail_openings)
                    "ed" -> KitsugiStrings.get(R.string.detail_endings)
                    "cm" -> KitsugiStrings.get(R.string.source_video_cm)
                    "clip" -> KitsugiStrings.get(R.string.source_video_clip)
                    else -> KitsugiStrings.get(R.string.source_video_trailer)
                }
                val label = if (rawName.isNotBlank()) "$kindLabel - $rawName" else kindLabel
                list.add(Pair(kind, KitsugiTheme(label = label, videoUrl = vUrl, source = "shikimori")))
            }
            list
        }.getOrElse { emptyList() }

        if (parsed.isNotEmpty()) {
            shikiVideosCache[animeId] = parsed
        }
        parsed
    }

    suspend fun fetchShikimoriExternalLinks(
        externalId: Int,
        mediaType: MediaType = MediaType.Anime
    ): List<KitsugiExternalLink> = withContext(Dispatchers.IO) {
        if (externalId <= 0) return@withContext emptyList()
        val endpoint = if (mediaType == MediaType.Manga) "mangas" else "animes"
        val url = runCatching { URL("$BASE_URL/$endpoint/$externalId/external_links") }.getOrNull()
            ?: return@withContext emptyList()
        runCatching {
            val response = KitsugiApiBase.executeGetRequestResilient(url) ?: return@runCatching emptyList()
            val array = JSONArray(response)
            val list = mutableListOf<KitsugiExternalLink>()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val lUrl = item.optNullableString("url") ?: continue
                val kind = item.optString("kind", "official_site")
                val siteName = when (kind.lowercase()) {
                    "official_site" -> KitsugiStrings.get(R.string.detail_link_official_site)
                    "wikipedia" -> KitsugiStrings.get(R.string.detail_link_wikipedia)
                    "crunchyroll" -> "Crunchyroll"
                    "twitter" -> "X (Twitter)"
                    "youtube" -> "YouTube"
                    "netflix" -> "Netflix"
                    "hidive" -> "HIDIVE"
                    "animenewsnetwork", "anime_news_network" -> "Anime News Network"
                    "myanimelist" -> "MyAnimeList"
                    "amazon_prime" -> "Amazon Prime"
                    "kinopoisk_hd" -> "Kinopoisk HD"
                    else -> kind.replace("_", " ").replaceFirstChar { it.uppercase() }
                }
                list.add(
                    KitsugiExternalLink(
                        site = siteName,
                        url = lUrl,
                        language = ShikimoriDetailParser.externalLinkLanguage(kind, lUrl)
                    )
                )
            }
            list
        }.getOrElse { emptyList() }
    }

    // ─── İlişkiler / Öneriler ─────────────────────────────────────────────

    /**
     * `/animes/{id}/similar` — Shikimori'nin "benzer yapımlar" listesi.
     *
     * Bu uç MAL/ARM eşlemesi gerektirmez ve Öneriler sekmesini tek istekle doldurur;
     * eskiden Shikimori kaydında bu sekme hiçbir veri kaynağına bağlı değildi ve
     * "Benzer yapım önerisi bulunamadı." çıkıyordu.
     */
    suspend fun fetchSimilarRecommendations(
        externalId: Int,
        mediaType: MediaType = MediaType.Anime
    ): List<KitsugiRelation> = withContext(Dispatchers.IO) {
        if (externalId <= 0) return@withContext emptyList()
        val endpoint = if (mediaType == MediaType.Manga) "mangas" else "animes"
        val url = runCatching { URL("$BASE_URL/$endpoint/$externalId/similar") }.getOrNull()
            ?: return@withContext emptyList()
        runCatching {
            KitsugiApiBase.runWithRateLimit {
                val response = KitsugiApiBase.executeGetRequestResilient(url)
                    ?: return@runWithRateLimit emptyList()
                val array = runCatching { JSONArray(response) }.getOrNull()
                    ?: return@runWithRateLimit emptyList()

                // Önce ham kayıtlar toplanır, kapaklar TEK toplu GraphQL isteğiyle çözülür
                // (her öğe için ayrı istek atmak öneri sekmesini dakikalarca bekletirdi).
                val parsed = mutableListOf<ShikiRelationSeed>()
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val id = item.optInt("id")
                    val title = item.optString("name").takeIf { it.isNotBlank() } ?: continue
                    if (id <= 0) continue
                    parsed.add(
                        ShikiRelationSeed(
                            id = id,
                            title = title,
                            relationType = "Recommendation",
                            entryType = if (mediaType == MediaType.Manga) MediaType.Manga
                            else kindToMediaType(item.optString("kind", "tv")),
                            kind = if (mediaType == MediaType.Manga) ShikimoriPosterResolver.Kind.MANGA
                            else ShikimoriPosterResolver.Kind.ANIME
                        )
                    )
                }
                buildRelationList(parsed)
            }
        }.getOrElse { err ->
            Log.e(TAG, "Shikimori similar exception: ${err.message}", err)
            emptyList()
        }
    }

    /**
     * `/animes/{id}/related` — Shikimori'nin ilişkili yapımları.
     *
     * Yanıt `[{relation, anime|{manga}, …}]` biçimindedir. İlişki tipi İngilizce gelir
     * ("Prequel", "Sequel", "Side story" …) ve mevcut Türkçe eşleme tablosuyla gösterilir.
     */
    suspend fun fetchRelatedRelations(
        externalId: Int,
        mediaType: MediaType = MediaType.Anime
    ): List<KitsugiRelation> = withContext(Dispatchers.IO) {
        if (externalId <= 0) return@withContext emptyList()
        val endpoint = if (mediaType == MediaType.Manga) "mangas" else "animes"
        val url = runCatching { URL("$BASE_URL/$endpoint/$externalId/related") }.getOrNull()
            ?: return@withContext emptyList()
        runCatching {
            KitsugiApiBase.runWithRateLimit {
                val response = KitsugiApiBase.executeGetRequestResilient(url)
                    ?: return@runWithRateLimit emptyList()
                val array = runCatching { JSONArray(response) }.getOrNull()
                    ?: return@runWithRateLimit emptyList()
                val parsed = mutableListOf<ShikiRelationSeed>()
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val relationType = item.optString("relation").takeIf { it.isNotBlank() } ?: "Relation"
                    // İlişkili kayıt anime ya da manga alt nesnesinde gelir.
                    val animeObj = item.optJSONObject("anime")
                    val mangaObj = item.optJSONObject("manga")
                    val sub = animeObj ?: mangaObj ?: continue
                    val kind = if (mangaObj != null && animeObj == null) ShikimoriPosterResolver.Kind.MANGA
                    else ShikimoriPosterResolver.Kind.ANIME
                    val id = sub.optInt("id")
                    val title = sub.optString("name").takeIf { it.isNotBlank() } ?: continue
                    if (id <= 0) continue
                    parsed.add(
                        ShikiRelationSeed(
                            id = id,
                            title = title,
                            relationType = relationType,
                            entryType = if (kind == ShikimoriPosterResolver.Kind.MANGA) MediaType.Manga
                            else kindToMediaType(sub.optString("kind", "tv")),
                            kind = kind
                        )
                    )
                }
                buildRelationList(parsed).distinctBy { "${it.malId}_${it.relationType}" }
            }
        }.getOrElse { err ->
            Log.e(TAG, "Shikimori related exception: ${err.message}", err)
            emptyList()
        }
    }

    /**
     * İlişki/öneri kayıtlarını toplayıp kapaklarını VE İngilizce/Japonca başlıklarını
     * eşzamanlı ve TOPLU çözer.
     *
     * - Başlık: Shikimori `name` (romaji). İngilizce/Japonca adlar [ShikimoriTitleResolver]'dan
     *   gelir; başlık dili ayarı (Romaji / İngilizce / Japonca) bunlara göre seçilir.
     * - Rusça (`russian`) alanı ARTIK KULLANILMAZ: eskiden `titleEnglish` olarak yazıldığı için
     *   İngilizce başlık seçildiğinde kartlarda Rusça adlar görünüyordu.
     * - İlişki tipi (Sequel, Side story, Adaptation …) Türkçeye çevrilir.
     */
    private suspend fun buildRelationList(seeds: List<ShikiRelationSeed>): List<KitsugiRelation> {
        if (seeds.isEmpty()) return emptyList()
        val kinds = seeds.map { it.kind }.distinct()
        return coroutineScope {
            val posterJobs = kinds.associateWith { kind ->
                async {
                    runCatching {
                        ShikimoriPosterResolver.resolve(kind, seeds.filter { it.kind == kind }.map { it.id })
                    }.getOrNull().orEmpty()
                }
            }
            val nameJobs = kinds.associateWith { kind ->
                async {
                    runCatching {
                        ShikimoriTitleResolver.resolve(kind, seeds.filter { it.kind == kind }.map { it.id })
                    }.getOrNull().orEmpty()
                }
            }
            val posters = posterJobs.mapValues { it.value.await() }
            val names = nameJobs.mapValues { it.value.await() }
            seeds.map { seed ->
                val nameInfo = names[seed.kind]?.get(seed.id)
                KitsugiRelation(
                    malId = seed.id,
                    title = seed.title,
                    relationType = seed.relationType.toTurkishRelationType(),
                    imageUrl = posters[seed.kind]?.get(seed.id)?.cardUrl,
                    mediaType = seed.entryType,
                    source = "shikimori",
                    titleEnglish = nameInfo?.english,
                    titleJapanese = nameInfo?.japanese,
                    titleRomaji = seed.title
                )
            }
        }
    }

    /** İlişki/öneri listesi için ham kayıt (kapak ve başlık çözümü sonra toplu yapılır). */
    private data class ShikiRelationSeed(
        val id: Int,
        val title: String,
        val relationType: String,
        val entryType: MediaType,
        val kind: ShikimoriPosterResolver.Kind
    )

    /** Shikimori `kind` alanını uygulamanın [MediaType] değerine çevirir. */
    private fun kindToMediaType(kind: String): MediaType = when (kind.lowercase()) {
        "movie" -> MediaType.Movie
        "manga", "manhwa", "manhua", "one_shot", "doujin" -> MediaType.Manga
        "tv", "tv_special", "special", "ova", "ona", "music", "pv", "cm" -> MediaType.Anime
        else -> MediaType.Anime
    }

    // ─── Karakter / Ekip fonksiyonları ────────────────────────────────────

    suspend fun fetchCharacters(
        mediaType: MediaType,
        externalId: Int
    ): List<KitsugiCharacter> {
        return withContext(Dispatchers.IO) {
            val endpoint = when (mediaType) {
                MediaType.Anime, MediaType.Movie, MediaType.TvShow -> "animes"
                MediaType.Manga -> "mangas"
            }
            val url = URL("$BASE_URL/$endpoint/$externalId/roles")
            Log.d(TAG, "Shikimori fetchCharacters: $url")
            runCatching {
                KitsugiApiBase.runWithRateLimit {
                    val response = KitsugiApiBase.executeGetRequestResilient(url) ?: return@runWithRateLimit emptyList()
                    val array = JSONArray(response)
                    val charactersMap = mutableMapOf<Int, KitsugiCharacter>()

                    for (i in 0 until array.length()) {
                        val item = array.optJSONObject(i) ?: continue
                        val charObj = item.optJSONObject("character") ?: continue
                        val charId = charObj.optInt("id")
                        if (charId <= 0) continue

                        val rolesArr = item.optJSONArray("roles")
                        val roleStr = if (rolesArr != null && rolesArr.length() > 0) {
                            rolesArr.optString(0)
                        } else "Supporting"

                        // Adlar burada ÇEVRİLMEZ: liste bittikten sonra toplu çevrilir
                        // (bkz. aşağıdaki translateCyrillicNames). Eskiden her ad için
                        // sırayla Google isteği atılıyordu; 24 karakter + 24 seiyuu'luk bir
                        // kadroda sekme dakikalarca skeleton'da kalıyordu.
                        val rawCharName = charObj.optString("name", "Bilinmeyen")
                        val relativeImg = charObj.optJSONObject("image")?.optString("original")
                        val imageUrl = ShikimoriPosterResolver.absoluteUrl(relativeImg)

                        val vaList = mutableListOf<KitsugiVoiceActor>()
                        val personObj = item.optJSONObject("person")
                        if (personObj != null) {
                            val vaId = personObj.optInt("id")
                            if (vaId > 0) {
                                val vaName = personObj.optString("name", "Bilinmeyen")
                                val vaRelativeImg = personObj.optJSONObject("image")?.optString("original")
                                val vaImageUrl = ShikimoriPosterResolver.absoluteUrl(vaRelativeImg)
                                vaList.add(
                                    KitsugiVoiceActor(
                                        id = vaId,
                                        name = vaName,
                                        language = "Japonca",
                                        imageUrl = vaImageUrl,
                                        source = "shikimori"
                                    )
                                )
                            }
                        }

                        val existing = charactersMap[charId]
                        if (existing != null) {
                            val updatedVas = (existing.voiceActors + vaList).distinctBy { it.id }
                            charactersMap[charId] = existing.copy(voiceActors = updatedVas)
                        } else {
                            charactersMap[charId] = KitsugiCharacter(
                                id = charId,
                                name = rawCharName,
                                role = roleStr.toTurkishCharacterRole(),
                                imageUrl = imageUrl,
                                voiceActors = vaList,
                                source = "shikimori"
                            )
                        }
                    }
                    // Tüm adları TEK seferde, sınırlı eşzamanlılıkla çevir
                    val names = charactersMap.values.flatMap { character ->
                        listOf(character.name) + character.voiceActors.map { it.name }
                    }
                    val translations = translateCyrillicNames(names)
                    val localizedCharacters = charactersMap.values.map { character ->
                        character.copy(
                            name = translations[character.name] ?: character.name,
                            voiceActors = character.voiceActors.map { actor ->
                                translations[actor.name]?.let { actor.copy(name = it) } ?: actor
                            }
                        )
                    }
                    withResolvedCharacterPosters(localizedCharacters)
                }
            }.getOrElse { err ->
                Log.e(TAG, "Shikimori fetchCharacters exception: ${err.message}", err)
                emptyList()
            }
        }
    }

    suspend fun fetchStaff(
        mediaType: MediaType,
        externalId: Int
    ): List<KitsugiStaff> {
        return withContext(Dispatchers.IO) {
            val endpoint = when (mediaType) {
                MediaType.Anime, MediaType.Movie, MediaType.TvShow -> "animes"
                MediaType.Manga -> "mangas"
            }
            val url = URL("$BASE_URL/$endpoint/$externalId/roles")
            Log.d(TAG, "Shikimori fetchStaff: $url")
            runCatching {
                KitsugiApiBase.runWithRateLimit {
                    val response = KitsugiApiBase.executeGetRequestResilient(url) ?: return@runWithRateLimit emptyList()
                    val array = JSONArray(response)
                    val staffList = mutableListOf<KitsugiStaff>()

                    for (i in 0 until array.length()) {
                        val item = array.optJSONObject(i) ?: continue
                        val charObj = item.optJSONObject("character")
                        if (charObj == null) {
                            val personObj = item.optJSONObject("person") ?: continue
                            val staffId = personObj.optInt("id")
                            if (staffId <= 0) continue

                            val rolesArr = item.optJSONArray("roles")
                            val roleStr = if (rolesArr != null && rolesArr.length() > 0) {
                                rolesArr.optString(0)
                            } else "Staff"

                            // Ad çevirisi liste bittikten SONRA toplu yapılır (aşağıda).
                            val staffName = personObj.optString("name", "Bilinmeyen")
                            val relativeImg = personObj.optJSONObject("image")?.optString("original")
                            val imageUrl = ShikimoriPosterResolver.absoluteUrl(relativeImg)

                            staffList.add(
                                KitsugiStaff(
                                    id = staffId,
                                    name = staffName,
                                    role = roleStr.toTurkishStaffRole(),
                                    imageUrl = imageUrl,
                                    source = "shikimori"
                                )
                            )
                        }
                    }
                    val translations = translateCyrillicNames(staffList.map { it.name })
                    val localizedStaff = staffList.map { person ->
                        translations[person.name]?.let { person.copy(name = it) } ?: person
                    }
                    withResolvedStaffPosters(localizedStaff)
                }
            }.getOrElse { err ->
                Log.e(TAG, "Shikimori fetchStaff exception: ${err.message}", err)
                emptyList()
            }
        }
    }

    suspend fun fetchCharacterDetail(characterId: Int): KitsugiCharacterDetail? {
        return withContext(Dispatchers.IO) {
            val url = URL("$BASE_URL/characters/$characterId")
            Log.d(TAG, "Shikimori fetchCharacterDetail: $url")
            runCatching {
                KitsugiApiBase.runWithRateLimit {
                    val response = KitsugiApiBase.executeGetRequestResilient(url) ?: return@runWithRateLimit null
                    val data = JSONObject(response)

                    // Adlar ve açıklama burada çevrilmez; tüm liste toplandıktan sonra
                    // TEK seferde (sınırlı eşzamanlılık + süre bütçesiyle) çevrilir.
                    val rawCharName = data.optString("name", "Bilinmeyen")
                    val nativeName = data.optNullableString("japanese")
                    val alternativeNames = mutableListOf<String>()
                    val altname = data.optNullableString("altname")
                    if (!altname.isNullOrBlank()) {
                        alternativeNames.add(altname)
                    }

                    val relativeImg = data.optJSONObject("image")?.optString("original")
                    val imageUrl = ShikimoriPosterResolver.absoluteUrl(relativeImg)
                    val rawBiography = data.optNullableString("description")?.cleanApiText()

                    val gender = null
                    val age = null
                    val birthday = null
                    val bloodType = null

                    val voiceActors = mutableListOf<KitsugiVoiceActor>()
                    val seyuArray = data.optJSONArray("seyu")
                    if (seyuArray != null) {
                        for (i in 0 until seyuArray.length()) {
                            val seyuItem = seyuArray.optJSONObject(i) ?: continue
                            val seyuId = seyuItem.optInt("id")
                            if (seyuId <= 0) continue

                            val seyuName = seyuItem.optString("name", "Bilinmeyen")
                            val seyuRelativeImg = seyuItem.optJSONObject("image")?.optString("original")
                            val seyuImageUrl = ShikimoriPosterResolver.absoluteUrl(seyuRelativeImg)

                            voiceActors.add(
                                KitsugiVoiceActor(
                                    id = seyuId,
                                    name = seyuName,
                                    language = "Japonca",
                                    imageUrl = seyuImageUrl,
                                    source = "shikimori"
                                )
                            )
                        }
                    }

                    val animeAppearances = mutableListOf<KitsugiCharacterMediaAppearance>()
                    val mangaAppearances = mutableListOf<KitsugiCharacterMediaAppearance>()
                    val animesArray = data.optJSONArray("animes")
                    if (animesArray != null) {
                        for (i in 0 until animesArray.length()) {
                            val animeItem = animesArray.optJSONObject(i) ?: continue
                            val animeId = animeItem.optInt("id")
                            if (animeId <= 0) continue

                            val animeTitle = animeItem.optString("name", "Bilinmeyen")
                            val animeRelativeImg = animeItem.optJSONObject("image")?.optString("original")
                            val animeImageUrl = ShikimoriPosterResolver.absoluteUrl(animeRelativeImg)
                            val kind = animeItem.optString("kind", "tv")

                            val rolesArr = animeItem.optJSONArray("roles")
                            val roleStr = if (rolesArr != null && rolesArr.length() > 0) {
                                rolesArr.optString(0)
                            } else "Supporting"

                            animeAppearances.add(
                                KitsugiCharacterMediaAppearance(
                                    mediaId = animeId,
                                    title = animeTitle,
                                    imageUrl = animeImageUrl,
                                    mediaType = kind.toTurkishMediaTypeString(),
                                    characterRole = roleStr.toTurkishCharacterRole(),
                                    source = "shikimori"
                                )
                            )
                        }
                    }

                    val mangasArray = data.optJSONArray("mangas")
                    if (mangasArray != null) {
                        for (i in 0 until mangasArray.length()) {
                            val mangaItem = mangasArray.optJSONObject(i) ?: continue
                            val mangaId = mangaItem.optInt("id")
                            if (mangaId <= 0) continue

                            val mangaTitle = mangaItem.optString("name", "Bilinmeyen")
                            val mangaRelativeImg = mangaItem.optJSONObject("image")?.optString("original")
                            val mangaImageUrl = ShikimoriPosterResolver.absoluteUrl(mangaRelativeImg)

                            val rolesArr = mangaItem.optJSONArray("roles")
                            val roleStr = if (rolesArr != null && rolesArr.length() > 0) {
                                rolesArr.optString(0)
                            } else "Supporting"

                            mangaAppearances.add(
                                KitsugiCharacterMediaAppearance(
                                    mediaId = mangaId,
                                    title = mangaTitle,
                                    imageUrl = mangaImageUrl,
                                    mediaType = "manga".toTurkishMediaTypeString(),
                                    characterRole = roleStr.toTurkishCharacterRole(),
                                    source = "shikimori"
                                )
                            )
                        }
                    }

                    // Eksik kapakları (karakter, seslendirmenler, yer aldığı yapımlar) toplu çöz.
                    val posters = PosterBatch()
                    posters.need(ShikimoriPosterResolver.Kind.CHARACTER, characterId, imageUrl)
                    voiceActors.forEach { posters.need(ShikimoriPosterResolver.Kind.PERSON, it.id, it.imageUrl) }
                    animeAppearances.forEach { posters.need(ShikimoriPosterResolver.Kind.ANIME, it.mediaId, it.imageUrl) }
                    mangaAppearances.forEach { posters.need(ShikimoriPosterResolver.Kind.MANGA, it.mediaId, it.imageUrl) }
                    posters.resolve()

                    // Kiril alfabesindeki adları ve yapım başlıklarını toplu çevir
                    val translations = translateCyrillicNames(
                        listOf(rawCharName) +
                            voiceActors.map { it.name } +
                            animeAppearances.map { it.title } +
                            mangaAppearances.map { it.title }
                    )
                    val charName = translations[rawCharName] ?: rawCharName
                    val localizedVoiceActors = voiceActors.map { actor ->
                        translations[actor.name]?.let { actor.copy(name = it) } ?: actor
                    }
                    val resolvedAppearances =
                        animeAppearances.map {
                            it.copy(
                                title = translations[it.title] ?: it.title,
                                imageUrl = posters.url(ShikimoriPosterResolver.Kind.ANIME, it.mediaId, it.imageUrl)
                            )
                        } + mangaAppearances.map {
                            it.copy(
                                title = translations[it.title] ?: it.title,
                                imageUrl = posters.url(ShikimoriPosterResolver.Kind.MANGA, it.mediaId, it.imageUrl)
                            )
                        }

                    KitsugiCharacterDetail(
                        id = characterId,
                        name = charName,
                        nativeName = nativeName,
                        alternativeNames = alternativeNames,
                        imageUrl = posters.url(ShikimoriPosterResolver.Kind.CHARACTER, characterId, imageUrl),
                        gender = gender,
                        age = age,
                        birthday = birthday,
                        bloodType = bloodType,
                        biography = translateWithBudget(rawBiography),
                        voiceActors = localizedVoiceActors.map {
                            it.copy(imageUrl = posters.url(ShikimoriPosterResolver.Kind.PERSON, it.id, it.imageUrl))
                        },
                        mediaAppearances = resolvedAppearances,
                        isFavourite = false,
                        source = "shikimori"
                    )
                }
            }.getOrNull()
        }
    }

    suspend fun fetchStaffDetail(staffId: Int): KitsugiStaffDetail? {
        return withContext(Dispatchers.IO) {
            val url = URL("$BASE_URL/people/$staffId")
            Log.d(TAG, "Shikimori fetchStaffDetail: $url")
            runCatching {
                KitsugiApiBase.runWithRateLimit {
                    val response = KitsugiApiBase.executeGetRequestResilient(url) ?: return@runWithRateLimit null
                    val data = JSONObject(response)

                    // Ad/biyografi/meslek metinleri tek tek değil, tüm liste toplandıktan
                    // sonra toplu çevrilir (aşağıda).
                    val rawStaffName = data.optString("name", "Bilinmeyen")
                    val nativeName = data.optNullableString("japanese")
                    val alternativeNames = mutableListOf<String>()
                    val rawBiography = data.optNullableString("biography")?.cleanApiText()

                    val rawBirthdayObj = data.opt("birth_on")
                    val rawBirthday = when (rawBirthdayObj) {
                        is JSONObject -> {
                            val d = rawBirthdayObj.optInt("day", 0).takeIf { it > 0 }
                            val m = rawBirthdayObj.optInt("month", 0).takeIf { it in 1..12 }
                            val y = rawBirthdayObj.optInt("year", 0).takeIf { it > 0 }
                            if (d != null && m != null && y != null) "$d.$m.$y"
                            else if (m != null && y != null) "$m.$y"
                            else if (y != null) "$y"
                            else null
                        }
                        is String -> rawBirthdayObj.takeIf { it.isNotBlank() && it != "null" }
                        else -> null
                    }
                    val (birthday, age) = com.kitsugi.animelist.utils.KitsugiDateUtils.formatBirthdayAndCalculateAge(rawBirthday, null)
                    val rawHomeTown = data.optNullableString("birth_place")
                    val gender = null
                    val rawOccupation = data.optNullableString("job_title")

                    val relativeImg = data.optJSONObject("image")?.optString("original")
                    val imageUrl = ShikimoriPosterResolver.absoluteUrl(relativeImg)

                    val characterRoles = mutableListOf<KitsugiStaffCharacterRole>()
                    // characterRoles ile aynı sırada: her rolün yapımı anime mi manga mı?
                    val roleMediaKinds = mutableListOf<ShikimoriPosterResolver.Kind?>()
                    val rolesArray = data.optJSONArray("roles")
                    if (rolesArray != null) {
                        for (i in 0 until rolesArray.length()) {
                            val roleObj = rolesArray.optJSONObject(i) ?: continue
                            val charObj = roleObj.optJSONObject("character") ?: continue
                            val animeObj = roleObj.optJSONObject("anime") ?: roleObj.optJSONObject("manga")

                            val charId = charObj.optInt("id")
                            val charName = charObj.optString("name", "Bilinmeyen")
                            val charRelativeImg = charObj.optJSONObject("image")?.optString("original")
                            val charImg = ShikimoriPosterResolver.absoluteUrl(charRelativeImg)

                            val mediaId = animeObj?.optInt("id") ?: 0
                            val mediaTitle = animeObj?.optString("name", "Bilinmeyen") ?: "Bilinmeyen"
                            val mediaRelativeImg = animeObj?.optJSONObject("image")?.optString("original")
                            val mediaImg = ShikimoriPosterResolver.absoluteUrl(mediaRelativeImg)
                            val mediaTypeStr = animeObj?.optString("kind", "tv") ?: "manga"

                            val roleStr = roleObj.optString("role", "Seyu") ?: "Seyu"

                            roleMediaKinds.add(
                                when {
                                    roleObj.optJSONObject("anime") != null -> ShikimoriPosterResolver.Kind.ANIME
                                    roleObj.optJSONObject("manga") != null -> ShikimoriPosterResolver.Kind.MANGA
                                    else -> null
                                }
                            )
                            characterRoles.add(
                                KitsugiStaffCharacterRole(
                                    characterId = charId,
                                    characterName = charName,
                                    characterImageUrl = charImg,
                                    characterSource = "shikimori",
                                    mediaId = mediaId,
                                    mediaTitle = mediaTitle,
                                    mediaImageUrl = mediaImg,
                                    mediaType = mediaTypeStr.toTurkishMediaTypeString(),
                                    characterRole = roleStr.toTurkishCharacterRole(),
                                    mediaSource = "shikimori"
                                )
                            )
                        }
                    }

                    val mediaWorks = mutableListOf<KitsugiStaffMediaWork>()
                    // mediaWorks ile aynı sırada: yapım anime mi manga mı?
                    val workMediaKinds = mutableListOf<ShikimoriPosterResolver.Kind?>()
                    val worksArray = data.optJSONArray("works")
                    if (worksArray != null) {
                        for (i in 0 until worksArray.length()) {
                            val workObj = worksArray.optJSONObject(i) ?: continue
                            val animeObj = workObj.optJSONObject("anime") ?: workObj.optJSONObject("manga") ?: continue
                            val mediaId = animeObj.optInt("id")
                            if (mediaId <= 0) continue

                            val mediaTitle = animeObj.optString("name", "Bilinmeyen") ?: "Bilinmeyen"
                            val mediaRelativeImg = animeObj.optJSONObject("image")?.optString("original")
                            val mediaImg = ShikimoriPosterResolver.absoluteUrl(mediaRelativeImg)
                            val mediaTypeStr = animeObj.optString("kind", "tv") ?: "tv"

                            val roleStr = workObj.optString("role", "Staff") ?: "Staff"

                            workMediaKinds.add(
                                if (workObj.optJSONObject("anime") != null) ShikimoriPosterResolver.Kind.ANIME
                                else ShikimoriPosterResolver.Kind.MANGA
                            )
                            mediaWorks.add(
                                KitsugiStaffMediaWork(
                                    mediaId = mediaId,
                                    mediaTitle = mediaTitle,
                                    mediaImageUrl = mediaImg,
                                    mediaType = mediaTypeStr.toTurkishMediaTypeString(),
                                    staffRole = roleStr.toTurkishStaffRole(),
                                    source = "shikimori"
                                )
                            )
                        }
                    }

                    // Eksik kapakları (kişi, karakterler, yapımlar) toplu çöz.
                    val posters = PosterBatch()
                    posters.need(ShikimoriPosterResolver.Kind.PERSON, staffId, imageUrl)
                    characterRoles.forEachIndexed { index, role ->
                        posters.need(ShikimoriPosterResolver.Kind.CHARACTER, role.characterId, role.characterImageUrl)
                        posters.need(roleMediaKinds.getOrNull(index), role.mediaId, role.mediaImageUrl)
                    }
                    mediaWorks.forEachIndexed { index, work ->
                        posters.need(workMediaKinds.getOrNull(index), work.mediaId, work.mediaImageUrl)
                    }
                    posters.resolve()

                    // Tüm adları ve yapım başlıklarını TEK seferde çevir
                    val translations = translateCyrillicNames(
                        listOf(rawStaffName) +
                            characterRoles.flatMap { listOf(it.characterName, it.mediaTitle) } +
                            mediaWorks.map { it.mediaTitle }
                    )
                    val staffName = translations[rawStaffName] ?: rawStaffName
                    val resolvedRoles = characterRoles.mapIndexed { index, role ->
                        role.copy(
                            characterName = translations[role.characterName] ?: role.characterName,
                            mediaTitle = translations[role.mediaTitle] ?: role.mediaTitle,
                            characterImageUrl = posters.url(
                                ShikimoriPosterResolver.Kind.CHARACTER, role.characterId, role.characterImageUrl
                            ),
                            mediaImageUrl = posters.url(
                                roleMediaKinds.getOrNull(index), role.mediaId, role.mediaImageUrl
                            )
                        )
                    }
                    val resolvedWorks = mediaWorks.mapIndexed { index, work ->
                        work.copy(
                            mediaTitle = translations[work.mediaTitle] ?: work.mediaTitle,
                            mediaImageUrl = posters.url(
                                workMediaKinds.getOrNull(index), work.mediaId, work.mediaImageUrl
                            )
                        )
                    }

                    KitsugiStaffDetail(
                        id = staffId,
                        name = staffName,
                        nativeName = nativeName,
                        alternativeNames = alternativeNames,
                        imageUrl = posters.url(ShikimoriPosterResolver.Kind.PERSON, staffId, imageUrl),
                        biography = translateWithBudget(rawBiography),
                        occupation = rawOccupation?.let { (translations[it] ?: it).toTurkishStaffRole() },
                        birthday = birthday,
                        age = age,
                        gender = gender,
                        homeTown = translateWithBudget(rawHomeTown),
                        characterRoles = resolvedRoles,
                        mediaWorks = resolvedWorks,
                        isFavourite = false
                    )
                }
            }.getOrNull()
        }
    }

    // ─── Poster (kapak) çözümleme yardımcıları ─────────────────────────────
    //
    // Shikimori REST API'sinin `image` alanı eski Paperclip ekine bakar ve yeni
    // yapımlarda `/assets/globals/missing_original.jpg` ("404 not found" görseli)
    // döndürür. Gerçek kapaklar yalnızca GraphQL'deki `poster` alanında bulunur.
    // Bu yüzden eksik kalan kayıtlar tür başına tek istekte toplu çözülür.
    // Ayrıntılı kök neden açıklaması: [ShikimoriPosterResolver]

    /**
     * Tür (anime/manga/karakter/kişi) başına tek GraphQL isteğiyle çözüm yapan
     * küçük toplu iş yardımcısı. Önce [need] ile eksikler toplanır, [resolve] ile
     * tek seferde çözülür, sonra [url] ile okunur.
     */
    private class PosterBatch {
        private val wanted = mutableMapOf<ShikimoriPosterResolver.Kind, MutableSet<Int>>()
        private val resolved = mutableMapOf<ShikimoriPosterResolver.Kind, Map<Int, ShikimoriPosterResolver.Poster>>()

        fun need(kind: ShikimoriPosterResolver.Kind?, id: Int, current: String?) {
            if (kind == null || current != null || id <= 0) return
            wanted.getOrPut(kind) { linkedSetOf() }.add(id)
        }

        suspend fun resolve() {
            for ((kind, ids) in wanted) {
                resolved[kind] = ShikimoriPosterResolver.resolve(kind, ids)
            }
        }

        fun url(kind: ShikimoriPosterResolver.Kind?, id: Int, current: String?): String? {
            if (current != null) return current
            if (kind == null || id <= 0) return null
            return resolved[kind]?.get(id)?.cardUrl
        }
    }

    /**
     * Shikimori REST araması yalnızca `name` (romaji) ve `russian` başlıklarını verir.
     * İngilizce/Japonca varyantları GraphQL'den sayfa başına toplu çözüp eksik kapaklarla
     * paralel tamamlarız; böylece arama ve keşfet kartları seçili başlık dilini kullanabilir.
     */
    private suspend fun withResolvedMediaMetadata(
        kind: ShikimoriPosterResolver.Kind,
        results: List<JikanSearchResult>
    ): List<JikanSearchResult> {
        if (results.isEmpty()) return results
        return coroutineScope {
            val posters = async { withResolvedPosters(kind, results) }
            val names = async {
                try {
                    ShikimoriTitleResolver.resolve(kind, results.map { it.malId })
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.w(TAG, "Shikimori başlıkları çözülemedi (${kind.field}): ${error.message}")
                    emptyMap<Int, ShikimoriTitleResolver.Names>()
                }
            }
            ShikimoriTitleResolver.applyToSearchResults(posters.await(), names.await())
        }
    }

    /** Arama/keşfet sonuçlarındaki eksik kapakları toplu tamamlar. */
    private suspend fun withResolvedPosters(
        kind: ShikimoriPosterResolver.Kind,
        results: List<JikanSearchResult>
    ): List<JikanSearchResult> {
        if (results.isEmpty()) return results
        val missingIds = results.filter { it.imageUrl == null && it.malId > 0 }.map { it.malId }
        if (missingIds.isEmpty()) return results
        val posters = ShikimoriPosterResolver.resolve(kind, missingIds)
        if (posters.isEmpty()) return results
        return results.map { item ->
            if (item.imageUrl != null) item
            else posters[item.malId]?.cardUrl?.let { item.copy(imageUrl = it) } ?: item
        }
    }

    /** Karakter listesindeki karakter ve seslendirmen görsellerini toplu tamamlar. */
    private suspend fun withResolvedCharacterPosters(
        characters: List<KitsugiCharacter>
    ): List<KitsugiCharacter> {
        if (characters.isEmpty()) return characters
        val batch = PosterBatch()
        characters.forEach { character ->
            batch.need(ShikimoriPosterResolver.Kind.CHARACTER, character.id, character.imageUrl)
            character.voiceActors.forEach { actor ->
                batch.need(ShikimoriPosterResolver.Kind.PERSON, actor.id, actor.imageUrl)
            }
        }
        batch.resolve()
        return characters.map { character ->
            character.copy(
                imageUrl = batch.url(ShikimoriPosterResolver.Kind.CHARACTER, character.id, character.imageUrl),
                voiceActors = character.voiceActors.map { actor ->
                    actor.copy(
                        imageUrl = batch.url(ShikimoriPosterResolver.Kind.PERSON, actor.id, actor.imageUrl)
                    )
                }
            )
        }
    }

    /** Ekip listesindeki kişi görsellerini toplu tamamlar. */
    private suspend fun withResolvedStaffPosters(staff: List<KitsugiStaff>): List<KitsugiStaff> {
        if (staff.isEmpty()) return staff
        val missingIds = staff.filter { it.imageUrl == null }.map { it.id }
        if (missingIds.isEmpty()) return staff
        val posters = ShikimoriPosterResolver.resolve(ShikimoriPosterResolver.Kind.PERSON, missingIds)
        if (posters.isEmpty()) return staff
        return staff.map { person ->
            if (person.imageUrl != null) person
            else posters[person.id]?.cardUrl?.let { person.copy(imageUrl = it) } ?: person
        }
    }

    /**
     * Kiril alfabesi içeren adları/başlıkları TEK toplu işte çevirir.
     *
     * Eski akış her ad için sırayla bir Google Translate isteği atıyordu; 24 karakter +
     * 24 seiyuu içeren bir kadroda bu onlarca ardışık istek demekti ve Karakterler/Ekip
     * sekmesi dakikalarca skeleton'da kalıyordu. Burada:
     *  - yalnızca Kiril içeren metinler işlenir,
     *  - en fazla [SHIKI_NAME_TRANSLATE_CONCURRENCY] istek paralel gider,
     *  - toplam süre [SHIKI_NAME_TRANSLATE_BUDGET_MS] ile sınırlıdır; yetişmeyen adlar
     *    özgün hâliyle gösterilir (sonraki açılışta Room önbelleğinden anında gelir).
     */
    private suspend fun translateCyrillicNames(
        texts: List<String>,
        budgetMs: Long = SHIKI_NAME_TRANSLATE_BUDGET_MS
    ): Map<String, String> {
        val targets = texts
            .map { it.trim() }
            .filter { it.isNotBlank() && it.any { ch -> ch in '\u0400'..'\u04FF' } }
            .distinct()
        if (targets.isEmpty()) return emptyMap()

        val context = com.kitsugi.animelist.KitsugiApplication.getInstance()?.applicationContext
            ?: return emptyMap()
        val translationManager = com.kitsugi.animelist.data.local.TranslationManager(context)
        val settings = runCatching {
            com.kitsugi.animelist.data.settings.SettingsDataStore(context).settingsFlow.first()
        }.getOrNull()
        val targetLang = settings?.translateTargetLanguage?.ifBlank { "tr" } ?: "tr"

        val result = java.util.concurrent.ConcurrentHashMap<String, String>()
        runCatching {
            withTimeoutOrNull(budgetMs) {
                targets.chunked(12).forEach { batch ->
                    val joined = batch.joinToString("\n")
                    val batchTranslated = runCatching {
                        translationManager.translateTo(joined, "auto", targetLang)
                    }.getOrNull()
                    val lines = batchTranslated?.split("\n")?.map { it.trim() }.orEmpty()
                    if (lines.size == batch.size && lines.all { it.isNotBlank() }) {
                        batch.forEachIndexed { idx, orig ->
                            result[orig] = lines[idx]
                        }
                    } else {
                        coroutineScope {
                            batch.chunked(SHIKI_NAME_TRANSLATE_CONCURRENCY).forEach { chunk ->
                                chunk.map { name ->
                                    async(Dispatchers.IO) {
                                        val translated = runCatching {
                                            translationManager.translateTo(name, "auto", targetLang)
                                        }.getOrNull()
                                        if (!translated.isNullOrBlank()) result[name] = translated
                                    }
                                }.forEach { it.await() }
                            }
                        }
                    }
                }
            }
        }
        return result.toMap()
    }

    /**
     * Uzun metni (özet/biyografi) süre bütçesiyle çevirir. Süre dolarsa veya çeviri
     * başarısız olursa özgün metin döner — sayfa çeviriyi asla beklemez.
     */
    private suspend fun translateWithBudget(
        text: String?,
        budgetMs: Long = SHIKI_TRANSLATE_TIMEOUT_MS
    ): String? {
        if (text.isNullOrBlank()) return text
        if (!text.any { it in '\u0400'..'\u04FF' }) return text
        val translated = runCatching {
            withTimeoutOrNull(budgetMs) { translateIfRussian(text) }
        }.getOrNull()
        return translated?.takeIf { it.isNotBlank() } ?: text
    }

    private suspend fun translateIfRussian(text: String?): String {
        if (text.isNullOrBlank()) return ""
        if (!text.any { it in '\u0400'..'\u04FF' }) return text

        val context = com.kitsugi.animelist.KitsugiApplication.getInstance()?.applicationContext
        return if (context != null) {
            val translationManager = com.kitsugi.animelist.data.local.TranslationManager(context)
            val settings = runCatching {
                com.kitsugi.animelist.data.settings.SettingsDataStore(context).settingsFlow.first()
            }.getOrNull()
            val targetLang = settings?.translateTargetLanguage?.ifBlank { "tr" } ?: "tr"
            translationManager.translateTo(text, "auto", targetLang)
        } else {
            directTranslate(text, "tr")
        }
    }

    private fun directTranslate(text: String, targetLang: String = "tr"): String {
        val cleaned = text.cleanShikimoriBbCode()
        return try {
            val urlStr = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$targetLang&dt=t&q=" + 
                    java.net.URLEncoder.encode(cleaned, "UTF-8")
            val request = okhttp3.Request.Builder()
                .url(urlStr)
                .header("User-Agent", "Mozilla/5.0")
                .build()
            com.kitsugi.animelist.core.network.KitsugiHttpClient.client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseText = response.body?.string() ?: return cleaned
                    val jsonArray = org.json.JSONArray(responseText)
                    val sentences = jsonArray.optJSONArray(0) ?: return cleaned
                    val result = java.lang.StringBuilder()
                    for (i in 0 until sentences.length()) {
                        val sentence = sentences.optJSONArray(i)
                        val translatedPart = sentence?.optString(0)
                        if (translatedPart != null) {
                            result.append(translatedPart)
                        }
                    }
                    result.toString().cleanShikimoriBbCode()
                } else {
                    cleaned
                }
            }
        } catch (e: java.lang.Exception) {
            cleaned
        }
    }
}
