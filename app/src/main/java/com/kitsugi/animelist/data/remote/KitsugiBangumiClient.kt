package com.kitsugi.animelist.data.remote

import android.content.Context
import android.util.Log
import com.kitsugi.animelist.data.auth.BangumiApiClient
import com.kitsugi.animelist.data.auth.BangumiApiClient.BangumiSubject
import com.kitsugi.animelist.data.auth.BangumiAuthStore
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.utils.PreferenceHelpers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Bangumi (bgm.tv) keşfet / arama / detay bağdaştırıcısı.
 *
 * Bu sınıf, [BangumiApiClient]'in Bangumi'ye özgü modellerini Kitsugi'nin kaynaklar arası
 * ortak modeli olan [JikanSearchResult]'a çevirir; böylece Keşfet (Explore), Arama ve
 * "Tümünü Gör" ızgaraları Bangumi'yi diğer 6 kaynakla aynı şekilde tüketebilir.
 *
 * Kimlik sözleşmesi: `JikanSearchResult.malId` alanında **Bangumi stableId** taşınır
 * (`subject_id + 500_000_000`, bkz. [BangumiIdNamespace]) ve `source = "bangumi"` olur.
 * Bu, Kitsu (300M) ve Shikimori (400M) ile birebir aynı desendir; aynı sayısal ID'ye sahip
 * bir MAL anime'si ile bir Bangumi条目'su asla aynı içerik sayılmaz.
 */
object KitsugiBangumiClient {

    init {
        // Keşfet/arama listeleri Latin adları önbellekten okur; ilk temasta diski UI
        // thread'inde okumamak için bellek aynası şimdiden doldurulmaya başlanır.
        BangumiTitleCache.warmUp()
    }

    private const val TAG = "KitsugiBangumiClient"

    const val SOURCE = BangumiIdNamespace.SOURCE

    /** Bangumi marka rengi (#F09199 — Aniyomi'nin `getLogoColor()` değeriyle aynı). */
    const val BRAND_COLOR_ARGB = 0xFFF09199

    // ── Oturum ───────────────────────────────────────────────────────────────

    /**
     * Keşfet/arama **oturum gerektirmez**; yalnızca NSFW içerik ve özel koleksiyonlar için
     * token gerekir. Token yoksa null döner ve istekler anonim yapılır.
     */
    private suspend fun tokenOrNull(context: Context?): String? {
        if (context == null) return null
        return try {
            if (BangumiAuthStore.isConnected(context)) BangumiAuthStore.getValidToken(context) else null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    // ── Arama (Arama sekmesi) ────────────────────────────────────────────────

    /**
     * Bangumi'de动画 arar.
     *
     * `sort = match` (eşleşme kalitesi) varsayılandır; "popülerlik" için
     * [BangumiApiClient.SearchSort.HEAT] verilebilir.
     */
    suspend fun searchAnime(
        query: String,
        limit: Int = 20,
        offset: Int = 0,
        context: Context? = null,
        includeAdult: Boolean = false
    ): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            searchWithFallback(
                keyword = query,
                token = tokenOrNull(context),
                types = listOf(BangumiApiClient.SubjectType.ANIME),
                sort = BangumiApiClient.SearchSort.MATCH,
                includeAdult = includeAdult,
                limit = limit,
                offset = offset
            ).filter { it.type == BangumiApiClient.SubjectType.ANIME }
                .filter { includeAdult || !it.nsfw }
                .map { it.toSearchResult(MediaType.Anime) }
                .withLatinBangumiNames(context)
        }.getOrElse { error ->
            Log.e(TAG, "Bangumi searchAnime failed: ${error.message}", error)
            emptyList()
        }
    }

    /**
     * Bangumi'de书籍 (manga/roman/画集) arar.
     * Aniyomi/Mihon deseniyle aynı: yalnızca `platform == "漫画"` olanlar manga sayılır,
     * böylece roman ve画集条目'ları manga listesini kirletmez.
     */
    suspend fun searchManga(
        query: String,
        limit: Int = 20,
        offset: Int = 0,
        context: Context? = null,
        includeAdult: Boolean = false,
        comicsOnly: Boolean = true,
        /**
         * `meta_tags` süzgeci (`电视剧`, `电影`, `漫画`, `小说`…). Bangumi'de `type = 6`
         * (三次元) içinde dizi ile filmi ayırmanın sunucu tarafındaki tek yolu; `type = 1`
         * içinde manga ile romanı ayırır.
         */
        metaTags: List<String> = emptyList()
    ): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            searchWithFallback(
                keyword = query,
                token = tokenOrNull(context),
                types = listOf(BangumiApiClient.SubjectType.BOOK),
                sort = BangumiApiClient.SearchSort.MATCH,
                includeAdult = includeAdult,
                limit = limit,
                offset = offset,
                metaTags = metaTags
            )
                .filter { it.type == BangumiApiClient.SubjectType.BOOK }
                .filter { includeAdult || !it.nsfw }
                .filter { !comicsOnly || it.platform == null || it.platform == "漫画" }
                .map { it.toSearchResult(MediaType.Manga) }.withLatinBangumiNames(context)
        }.getOrElse { error ->
            Log.e(TAG, "Bangumi searchManga failed: ${error.message}", error)
            emptyList()
        }
    }

    /**
     * Karakter (角色) araması — `POST /v0/search/characters` (deneysel uç).
     * Shikimori/Kitsu karakter aramalarıyla aynı çıktı biçimi: [JikanSearchResult] içinde
     * `subtitle` "Karakter (Bangumi)" olarak işaretlenir.
     */
    suspend fun searchCharacters(
        query: String,
        page: Int = 1,
        limit: Int = BangumiApiClient.SEARCH_PAGE_SIZE,
        context: Context? = null,
        includeAdult: Boolean = false
    ): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        runCatching {
            BangumiApiClient.searchCharacters(
                keyword = query.trim(),
                token = tokenOrNull(context),
                nsfw = if (includeAdult) null else false,
                limit = limit,
                offset = (page - 1).coerceAtLeast(0) * limit
            ).data.map { it.toSearchResult("Karakter (Bangumi)") }
        }.getOrElse { logAndEmpty("searchCharacters", it) }
    }

    /** Kişi/seslendirmen (人物) araması — `POST /v0/search/persons` (deneysel uç). */
    suspend fun searchPeople(
        query: String,
        page: Int = 1,
        limit: Int = BangumiApiClient.SEARCH_PAGE_SIZE,
        context: Context? = null,
        career: List<String> = emptyList()
    ): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        runCatching {
            BangumiApiClient.searchPersons(
                keyword = query.trim(),
                token = tokenOrNull(context),
                career = career,
                limit = limit,
                offset = (page - 1).coerceAtLeast(0) * limit
            ).data.map {
                val careerLabel = it.career.firstOrNull()?.let { c -> " · $c" } ?: ""
                it.toSearchResult("Kişi (Bangumi)$careerLabel")
            }
        }.getOrElse { logAndEmpty("searchPeople", it) }
    }
    /**
     * "Tümünü Gör" ızgarası için sayfalı gelişmiş arama.
     *
     * @param page 1 tabanlı sayfa numarası (Bangumi `offset` kullanır → `(page-1)*limit`)
     */
    suspend fun searchMediaAdvanced(
        mediaType: MediaType,
        query: String = "",
        page: Int = 1,
        limit: Int = 24,
        sort: String = BangumiApiClient.SearchSort.MATCH,
        tags: List<String> = emptyList(),
        yearFrom: Int? = null,
        yearTo: Int? = null,
        minScore: Int? = null,
        context: Context? = null,
        includeAdult: Boolean = false,
        /**
         * Sunucuya gönderilecek `filter.type` listesini zorlar.
         *
         * `MediaType.Movie` normalde hem anime filmlerini (2) hem gerçek filmleri (6)
         * kapsar; "Diziler/Filmler" (canlı çekim) kapsamında ise yalnızca `type=6`
         * istenmelidir, aksi hâlde film rafına anime filmleri karışıyordu.
         */
        subjectTypes: List<Int>? = null,
        /**
         * `type = 1` (书籍) sonuçlarını yalnızca **manga** ile sınırla.
         *
         * Bangumi书籍 kataloğu manga (`平台 = 漫画`), roman (`小说`), 画集 ve 设定集
         * kayıtlarını birlikte döndürür. Arama sayfasının "Manga" kapsamı roman
         * listeliyordu; `platform` alanı sunucuda süzülemediği için (v0'da `platform`
         * yalnız oyun türünde çalışır) süzgeç burada uygulanır.
         */
        comicsOnly: Boolean = true,
        /**
         * `meta_tags` süzgeci (`电视剧`, `电影`, `漫画`, `小说`…). Bangumi'de `type = 6`
         * (三次元) içinde dizi ile filmi ayırmanın sunucu tarafındaki tek yolu; `type = 1`
         * içinde manga ile romanı ayırır.
         */
        metaTags: List<String> = emptyList(),
        /**
         * Mevsim süzgeci (`WINTER`/`SPRING`/`SUMMER`/`FALL`) + yılı.
         *
         * Bangumi'de ayrı bir sezon alanı yok; v0 `filter.air_date` **tarih aralığı**
         * kabul ediyor. Detay sayfasındaki "Kış 2025" gibi bir facet seçildiğinde
         * yalnız yıl göndermek çeyrek yıl yerine tüm yılı süzüyordu (istenen kayıtlar
         * listenin içinde kalıyor ama başka mevsimler de geliyordu). Çeyrek aralık
         * canlı probla doğrulandı: `probe/result.txt` V1–V5 (`>=2025-01-01` +
         * `<2025-04-01` → 89 kayıt; `<=` ve `<` biçimlerinin ikisi de çalışıyor).
         */
        season: String? = null,
        seasonYear: Int? = null,
        /**
         * Sunucuda **dışlama** süzgeci yok (v0 `filter.tag` yalnız "içerir").
         * `GenresTagsSheet`'te bir tür "hariç tut" olarak işaretlendiğinde bu liste dolar ve
         * süzgeç istemcide uygulanır: kaydın `tags`/`meta_tags` alanı hariç tutulan
         * etiketlerden birini içeriyorsa kayıt atılır.
         */
        excludedTags: List<String> = emptyList()
    ): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        try {
            val types = subjectTypes ?: when (mediaType) {
                MediaType.Manga -> listOf(BangumiApiClient.SubjectType.BOOK)
                MediaType.Movie -> listOf(BangumiApiClient.SubjectType.ANIME, BangumiApiClient.SubjectType.REAL)
                MediaType.TvShow -> listOf(BangumiApiClient.SubjectType.REAL)
                MediaType.Anime -> listOf(BangumiApiClient.SubjectType.ANIME)
            }
            val airDate = mutableListOf<String>()
            // Mevsim + yıl verilmişse çeyrek aralık yıl aralığından **önce** gelir:
            // ikisi birlikte gönderilirse sunucu kesişimi alır (daha dar olan kazanır).
            val seasonRange = bangumiSeasonRange(season, seasonYear)
            if (seasonRange != null) {
                airDate += ">=${seasonRange.first}"
                airDate += "<${seasonRange.second}"
            } else {
                yearFrom?.let { airDate += ">=$it-01-01" }
                yearTo?.let { airDate += "<=${it}-12-31" }
            }
            val rating = minScore?.takeIf { it > 0 }?.let { listOf(">=$it") } ?: emptyList()

            val effectiveQuery = query.trim()
            // Bangumi etiket süzgeci sunucuda **birebir** karşılaştırılır (`tag = "科幻"`).
            // Arayüzden gelen Türkçe/İngilizce çipler (`Aksiyon`, `Sci-Fi`, `School`) bu
            // yüzden her zaman sıfır sonuç veriyordu; burada sözlükten geçirilip Bangumi'nin
            // kendi yazımına çevriliyor. Sözlükte bulunamayan ve zaten CJK olmayan terim
            // gönderilmez (aksi hâlde "süzgeç uygulanmış" görünen boş liste dönerdi).
            val effectiveTags = tags.mapNotNull { raw ->
                val mapped = com.kitsugi.animelist.utils.BangumiTagDictionary.canonicalTagFor(raw)
                if (mapped == null) Log.w(TAG, "Bangumi etiketi sözlükte yok, atlandı: $raw")
                mapped
            }.distinct()
            // Arama ucu sayfa başına en fazla 20 kayıt verir; offset de AYNI boyuta göre
            // hesaplanmazsa her sayfada kayıt atlanır. Göz atma ucu ise 50'ye kadar destekler.
            val pageSize = if (effectiveQuery.isBlank()) limit else limit.coerceIn(1, BangumiApiClient.SEARCH_PAGE_SIZE)
            val offset = ((page - 1).coerceAtLeast(0)) * pageSize

            // Göz atma ucu (`GET /v0/subjects`) yalnızca `sort=date|rank` ve **tek** tür
            // destekler; etiket/tarih/puan süzgeci de yoktur. Bu yüzden:
            //   • süzgeç varsa, tür birden fazlaysa (film = anime + REAL) ya da sıralama
            //     `heat`/`score` ise → arama ucu,
            //   • notr/sıralamasız boş isteklerde → ucuz ve sunucuda önbelleklenen göz atma.
            // Eski kod her boş isteği göz atmaya yolluyor ve `heat`/`score` sıralamalarını
            // sessizce `date`e çeviriyordu: "Popüler" ızgarası popülerliğe göre değil, en
            // yeniye göre sıralanıyordu ve film kategorisinde REAL yapıtlar hiç gelmiyordu.
            // `type = 1` (书籍) kataloğu manga + roman + 画集 karışık döner ve `platform`
            // sunucuda süzülemez (v0: `platform` yalnız oyun türünde çalışır). Yalnız
            // istemcide süzmek sayfalamayı bozuyordu: prob P5'te `BOOK + tag 恋爱` ilk
            // 5 sonucunun **tamamı roman** çıktı, istemci süzgecinden sonra sayfa boş
            // kalıp "sonraki sayfa yok" kararı veriliyordu. Q1/Q2/Q3 probları `漫画`
            // meta etiketinin sunucuda doğru çalıştığını gösterdi.
            val effectiveMetaTags = when {
                mediaType == MediaType.Manga && comicsOnly -> metaTags + "漫画"
                else -> metaTags
            }.distinct()
            // `meta_tags` de bir süzgeçtir: göz atma ucu (`GET /v0/subjects`) bu alanı
            // bilmediği için sessizce yok sayardı. "Bangumi Dizileri/Filmleri" ızgarası
            // `type=6` + `meta_tags` ile ayrıştığından, meta süzgeci varken **arama ucu**
            // kullanılmak zorunda (probe F1/F2/Q1–Q4).
            val hasSearchFilters = effectiveTags.isNotEmpty() || airDate.isNotEmpty() ||
                rating.isNotEmpty() || effectiveMetaTags.isNotEmpty()
            val wantsSearchSort = sort == BangumiApiClient.SearchSort.HEAT ||
                sort == BangumiApiClient.SearchSort.SCORE
            val canBrowse = effectiveQuery.isBlank() && !hasSearchFilters &&
                !wantsSearchSort && types.size == 1
            val subjects = if (canBrowse) {
                BangumiApiClient.browseSubjects(
                    type = types.first(),
                    token = tokenOrNull(context),
                    sort = if (sort == BangumiApiClient.SearchSort.RANK) "rank" else "date",
                    year = yearFrom ?: yearTo,
                    limit = pageSize,
                    offset = offset
                ).data
            } else {
                // Puan sıralamasında oy tabanı olmadan listenin tepesi tek oylu "10/10"
                // kayıtlarla dolar (bkz. BangumiApiClient.topScored). Yalnızca anahtar
                // kelimesiz puan sıralamasında uygulanır; kullanıcı süzgeci varsa
                // (ör. minScore) kullanıcı ne istediğini söylemiştir.
                val ratingCount = if (sort == BangumiApiClient.SearchSort.SCORE &&
                    effectiveQuery.isBlank() && rating.isEmpty()
                ) {
                    listOf(">=${BangumiApiClient.MIN_RATING_VOTES}")
                } else {
                    emptyList()
                }
                // `sort=rank` Meilisearch'te `rank:asc` demektir; sıralamaya girmemiş
                // kayıtların `rank` alanı **0** olduğu için listenin tepesine otururlar.
                // 2026-10-31 canlı API (G1): etiket süzgeçli `sort=rank` sorgusu oy
                // sayısı 18 olan niş yapıtlarla başlıyordu. `rank>=1`, resmî 排行榜
                // semantiğini geri getirir (browse ucu bunu zaten kendisi uygular).
                // Anahtar kelime yokken `rank` ve `match` sıralamaları eşit dereceli
                // olduğundan Meilisearch'ün varsayılan sırası geçerli olur ve `rank = 0`
                // kayıtlar (hiçbir sıralamaya girmemiş, oy almamış yapıtlar) başa düşer
                // (probe S: `剧情` 695 kayıt → ilk üç sonuç oy sayısı 34–45, rank 0).
                // `heat`/`score` ise kendi doğal ölçütünü taşır; onlara dokunulmaz.
                val rankFilter = if (
                    effectiveQuery.isBlank() &&
                    sort != BangumiApiClient.SearchSort.HEAT &&
                    sort != BangumiApiClient.SearchSort.SCORE
                ) {
                    listOf(">=1")
                } else {
                    emptyList()
                }
                searchWithFallback(
                    keyword = effectiveQuery,
                    token = tokenOrNull(context),
                    types = types,
                    sort = sort,
                    tags = effectiveTags,
                    airDate = airDate,
                    rating = rating,
                    includeAdult = includeAdult,
                    limit = pageSize,
                    offset = offset,
                    ratingCount = ratingCount,
                    rank = rankFilter,
                    metaTags = effectiveMetaTags
                )
            }
            // Hiçbir etiket sunucunun anladığı yazıma çevrilemediyse **filtresiz liste
            // döndürmek** "süzgeç uygulanmış" izlenimi verir; boş anahtar kelimede boş
            // liste daha dürüst (ve sayfalama döngüsünü kırar).
            if (tags.isNotEmpty() && effectiveTags.isEmpty() && effectiveQuery.isBlank()) {
                Log.w(TAG, "Bangumi etiketleri çevrilemedi, süzgeçsiz liste döndürülmedi: $tags")
                return@withContext emptyList()
            }
            val excludedNative = excludedTags
                .mapNotNull { com.kitsugi.animelist.utils.BangumiTagDictionary.canonicalTagFor(it) }
            subjects
                .filter { BangumiIdNamespace.supportsSubjectType(it.type) }
                .filter { subject ->
                    excludedNative.isEmpty() || excludedNative.none { banned ->
                        subject.tags.contains(banned) || subject.metaTags.contains(banned)
                    }
                }
                // Manga aramasında roman/画集/資料集 kayıtları listeyi kirletiyordu
                // (2026-10-31 canlı API: "one piece" aramasının 2. sonucu `海贼王 小说` —
                // platform 小说). `searchManga` ile aynı sözleşme burada da uygulanır:
                // platform bilinmiyorsa (null) kayıt korunur.
                .filter {
                    mediaType != MediaType.Manga || !comicsOnly ||
                        it.platform == null || it.platform == "漫画"
                }
                .filter { includeAdult || !it.nsfw }
                .map { it.toSearchResult(mediaType) }.withLatinBangumiNames(context)
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            // Hata artık sessizce "Sonuç bulunamadı"ya çevrilmiyor: arama ekranı gerçek hata
            // mesajını gösterir (ör. "Bangumi HTTP 400 ..."), böylece teşhis mümkün olur.
            Log.e(TAG, "Bangumi searchMediaAdvanced failed: ${error.message}", error)
            throw error
        }
    }

    /**
     * Anahtar kelime araması: önce `POST /v0/search/subjects`, hata verirse ya da boş dönerse
     * (ve süzgeç yoksa) eski `GET /search/subject/{q}` ucu denenir.
     *
     * NSFW: sunucu `filter.nsfw` alanını JSON boolean olarak bekler. Yetişkin içerik kapalıyken
     * `false` gönderilir (sayfa boyutu sunucuda süzüldüğü için sayfalama bozulmaz); açıkken
     * filtre hiç gönderilmez (R18, yalnızca yetkili hesaplara döner).
     *
     * İki uç da hata verirse ilk (v0) hata fırlatılır; ikisi de boşsa boş liste döner.
     */
    private suspend fun searchWithFallback(
        keyword: String,
        token: String?,
        types: List<Int>,
        sort: String,
        includeAdult: Boolean,
        limit: Int,
        offset: Int,
        tags: List<String> = emptyList(),
        airDate: List<String> = emptyList(),
        rating: List<String> = emptyList(),
        ratingCount: List<String> = emptyList(),
        /** `rank` filtresi (`>=1`). Puan sıralamasında `rank = 0` kayıtları eler. */
        rank: List<String> = emptyList(),
        /** `meta_tags` (`漫画`, `小说`, `电视剧`…). `type = 1` içinde manga/roman ayrımı. */
        metaTags: List<String> = emptyList()
    ): List<BangumiSubject> {
        val primary = runCatching {
            BangumiApiClient.searchSubjects(
                keyword = keyword,
                token = token,
                types = types,
                sort = sort,
                tags = tags,
                airDate = airDate,
                rating = rating,
                rank = rank,
                nsfw = if (includeAdult) null else false,
                limit = limit,
                offset = offset,
                ratingCount = ratingCount,
                metaTags = metaTags
            )
        }
        primary.exceptionOrNull()?.let { if (it is kotlinx.coroutines.CancellationException) throw it }
        val primaryData = primary.getOrNull()?.data.orEmpty()
        if (primaryData.isNotEmpty()) return primaryData

        // Eski uç tag/tarih/puan süzgeci desteklemez; süzgeçli aramada yedeğe düşme.
        // `ratingCount` bir sıralama kalitesi süzgecidir, yedeğe düşmeyi engellemez.
        val plainQuery = tags.isEmpty() && airDate.isEmpty() && rating.isEmpty()
        if (!plainQuery || keyword.isBlank()) {
            primary.exceptionOrNull()?.let { throw it }
            return emptyList()
        }

        val legacyTypes: List<Int?> = if (types.isEmpty()) listOf<Int?>(null) else types
        val legacy = runCatching {
            val merged = LinkedHashMap<Int, BangumiSubject>()
            for (type in legacyTypes) {
                BangumiApiClient.searchSubjectsLegacy(
                    keyword = keyword,
                    token = token,
                    type = type,
                    limit = limit,
                    offset = offset
                ).data.forEach { merged.putIfAbsent(it.id, it) }
            }
            merged.values.toList()
        }
        legacy.exceptionOrNull()?.let { if (it is kotlinx.coroutines.CancellationException) throw it }
        val legacyData = legacy.getOrNull().orEmpty()
        if (legacyData.isNotEmpty()) {
            Log.w(TAG, "Bangumi v0 arama sonuç vermedi (${primary.exceptionOrNull()?.message}); legacy uç ${legacyData.size} sonuç döndürdü")
            return legacyData
        }
        primary.exceptionOrNull()?.let { throw it }
        legacy.exceptionOrNull()?.let { throw it }
        return emptyList()
    }

    /**
     * Arama/keşfet uçlarının kısa subject yanıtında infobox bulunmaz. Bu yüzden yalnızca
     * doğrudan Bangumi alanlarına bakmak yetmez: başlık subject türüne göre AniList veya katı
     * TMDB eşleşmesiyle, gerekirse subject infobox'ıyla tamamlanır. Romaji-only kayıt İngilizce
     * başlık varmış gibi kabul edilmez.
     */
    @Suppress("UNUSED_PARAMETER")
    internal suspend fun List<JikanSearchResult>.withLatinBangumiNames(context: Context? = null): List<JikanSearchResult> {
        val requests = mapNotNull { item ->
            if (!item.source.equals(SOURCE, ignoreCase = true)) return@mapNotNull null
            val rawId = BangumiIdNamespace.rawIdFromStable(item.malId) ?: item.malId
            if (rawId <= 0 || hasReliableEnglish(item)) return@mapNotNull null
            val nativeTitle = item.titleJapanese?.trim()?.takeIf { it.isNotEmpty() } ?: item.title
            if (nativeTitle.isBlank()) return@mapNotNull null
            BangumiSubjectTitleResolver.Request(
                rawId = rawId,
                nativeTitle = nativeTitle,
                mediaType = titleLookupMediaType(item),
                subjectType = item.bangumiSubjectType
            )
        }
        if (requests.isEmpty()) return map { applyLocalizedBangumiTitles(it, BangumiTitleCache.get(rawBangumiId(it))) }

        // Liste modu: yalnızca kalıcı önbellek + tek AniList geçişi. Per-subject infobox
        // çözümlemesi burada YAPILMAZ; Keşfet/Arama şeritleri aynı anda açıldığında yüzlerce
        // Bangumi isteği üretip tüm uygulamayı dakikalarca kilitliyordu.
        val resolved = try {
            BangumiSubjectTitleResolver.resolve(requests, listMode = true)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "Bangumi arama/keşfet başlıkları tamamlanamadı: ${error.message}")
            emptyMap()
        }
        return map { item ->
            if (!item.source.equals(SOURCE, ignoreCase = true)) return@map item
            val rawId = rawBangumiId(item)
            val titles = rawId?.let { resolved[it] } ?: BangumiTitleCache.get(rawId)
            applyLocalizedBangumiTitles(item, titles)
        }
    }

    private fun rawBangumiId(item: JikanSearchResult): Int? =
        if (!item.source.equals(SOURCE, ignoreCase = true)) null
        else BangumiIdNamespace.rawIdFromStable(item.malId) ?: item.malId.takeIf { it > 0 }

    private fun titleLookupMediaType(item: JikanSearchResult): MediaType = when (item.bangumiSubjectType) {
        BangumiApiClient.SubjectType.ANIME -> MediaType.Anime
        BangumiApiClient.SubjectType.BOOK -> MediaType.Manga
        BangumiApiClient.SubjectType.REAL -> item.type
        else -> item.type
    }

    private fun hasReliableEnglish(item: JikanSearchResult): Boolean =
        BangumiSubjectTitleResolver.hasReliableEnglish(
            BangumiTitleCache.LatinTitles(
                romaji = item.titleRomaji,
                english = item.titleEnglish,
                native = item.titleJapanese
            )
        )

    /** Safely copies each language variant into its own display field. */
    internal fun applyLocalizedBangumiTitles(
        item: JikanSearchResult,
        titles: BangumiTitleCache.LatinTitles?
    ): JikanSearchResult {
        if (!item.source.equals(SOURCE, ignoreCase = true)) return item
        val itemEnglish = com.kitsugi.animelist.utils.MediaTitleResolver.latin(item.titleEnglish)
        val itemRomaji = com.kitsugi.animelist.utils.MediaTitleResolver.latin(item.titleRomaji)
        val cachedEnglish = com.kitsugi.animelist.utils.MediaTitleResolver.latin(titles?.english)
        val cacheReliable = BangumiSubjectTitleResolver.hasReliableEnglish(titles)
        val itemReliable = BangumiSubjectTitleResolver.hasReliableEnglish(
            BangumiTitleCache.LatinTitles(itemRomaji, itemEnglish, item.titleJapanese)
        )
        val english = when {
            cacheReliable -> cachedEnglish
            itemReliable -> itemEnglish
            else -> null
        }
        val romaji = com.kitsugi.animelist.utils.MediaTitleResolver.latin(titles?.romaji) ?: itemRomaji
        val native = item.titleJapanese?.takeIf { it.isNotBlank() }
            ?: titles?.native?.takeIf { it.isNotBlank() }
        val title = romaji ?: english ?: item.title
        return item.copy(
            title = title,
            titleEnglish = english,
            titleJapanese = native,
            titleRomaji = romaji
        )
    }

    // ── Keşfet kategorileri ──────────────────────────────────────────────────

    /** "En İyi Animeler" — `sort=rank` ile kategori sıralaması (排行榜). */
    suspend fun topAnime(limit: Int = 20, context: Context? = null, offset: Int = 0): List<JikanSearchResult> =
        runCatching {
            BangumiApiClient.topRanked(BangumiApiClient.SubjectType.ANIME, tokenOrNull(context), limit, offset)
                .data.map { it.toSearchResult(MediaType.Anime) }.withLatinBangumiNames(context)
        }.getOrElse { logAndEmpty("topAnime", it) }

    /** "Popüler Animeler" — koleksiyon sayısı (收藏人数) sıralaması. */
    suspend fun trendingAnime(limit: Int = 20, context: Context? = null, offset: Int = 0): List<JikanSearchResult> =
        runCatching {
            BangumiApiClient.mostCollected(BangumiApiClient.SubjectType.ANIME, tokenOrNull(context), limit, offset)
                .data.map { it.toSearchResult(MediaType.Anime) }.withLatinBangumiNames(context)
        }.getOrElse { logAndEmpty("trendingAnime", it) }

    /**
     * "En Yüksek Puanlı Animeler".
     *
     * `sort = score` **tek başına yanlış sonuç verir**: Bangumi puan sıralaması oy sayısını
     * hesaba katmadığı için tek oylu bir 10/10 kayıt binlerce oylu yapıtların önüne geçiyordu
     * (2026-10-31 canlı API: ilk sonuç `rating.total = 1`). [BangumiApiClient.topScored]
     * Bangumi'nin 排行榜 sırasını oy tabanıyla birleştirir; böylece şerit gerçekten
     * "en yüksek puanlı" yapıtları gösterir.
     */
    suspend fun topRatedAnime(limit: Int = 20, context: Context? = null, offset: Int = 0): List<JikanSearchResult> =
        runCatching {
            BangumiApiClient.topScored(
                type = BangumiApiClient.SubjectType.ANIME,
                token = tokenOrNull(context),
                limit = limit,
                offset = offset,
                minVotes = BangumiApiClient.MIN_RATING_VOTES
            ).data.map { it.toSearchResult(MediaType.Anime) }.withLatinBangumiNames(context)
                .ifEmpty { topAnime(limit, context, offset) }
        }.getOrElse { logAndEmpty("topRatedAnime", it) }

    /**
     * "Bu Sezon" — içinde bulunulan ayın动画条目'ları, yayın tarihine göre.
     * Bangumi'de sezon "kış/ilkbahar/yaz/sonbahar" değil **yıl+ay** ile ifade edilir.
     */
    /**
     * **Bu sezon** — içinde bulunulan takvim çeyreğinin tamamı.
     *
     * Eski davranış `seasonFor(year, currentMonth)` idi: yani "Bu Sezon" rafı aslında yalnız
     * **bu ay** yayınlananları listeliyordu (bir mevsimin ~⅓'ü) ve "Tümünü Gör" sayfası da
     * aynı dar listeyi büyütmeye çalışıyordu. Artık `air_date` çeyrek aralığı kullanılır
     * (canlı prob `probe/result.txt` V1–V5: `>=2025-01-01` + `<2025-04-01` → 89 kayıt);
     * sıralama `heat` (koleksiyona ekleme) çünkü sezon rafı popülerliği yansıtmalı.
     *
     * Arama ucu boş dönerse eski ay bazlı göz atma yoluna düşülür (asla boş raf bırakma).
     */
    suspend fun seasonalAnime(
        limit: Int = 20,
        context: Context? = null,
        offset: Int = 0
    ): List<JikanSearchResult> {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val season = when (month) {
            in 1..3 -> "WINTER"
            in 4..6 -> "SPRING"
            in 7..9 -> "SUMMER"
            else -> "FALL"
        }
        val quarter = searchMediaAdvanced(
            mediaType = MediaType.Anime,
            query = "",
            page = (offset / limit.coerceAtLeast(1)) + 1,
            limit = limit,
            sort = BangumiApiClient.SearchSort.HEAT,
            context = context,
            season = season,
            seasonYear = year
        )
        if (quarter.isNotEmpty()) return quarter
        return seasonFor(year = year, month = month, limit = limit, context = context, offset = offset)
    }

    /** Belirli yıl/ay için göz atma (`GET /v0/subjects?type=2&year=..&month=..&sort=date`). */
    suspend fun seasonFor(
        year: Int,
        month: Int? = null,
        limit: Int = 20,
        context: Context? = null,
        offset: Int = 0
    ): List<JikanSearchResult> = runCatching {
        BangumiApiClient.browseSeason(year, month, tokenOrNull(context), limit, offset)
            .data.map { it.toSearchResult(MediaType.Anime) }.withLatinBangumiNames(context)
    }.getOrElse { logAndEmpty("seasonFor($year-$month)", it) }

    /**
     * "Şu An Yayında" — bu ay + geçen ay birleşimi (Bangumi'de airing durumu yoktur).
     *
     * `offset` sayfalama için: ilk sayfa (offset = 0) iki ayı birleştirir; sonraki sayfalar
     * geçerli ayın devamını verir. Parametre olmadığında "Tümünü Gör" sayfası her sayfada
     * **aynı 20 kaydı** istiyor, liste büyümediği için sayfalama kilitleniyordu.
     */
    suspend fun airingAnime(
        limit: Int = 20,
        context: Context? = null,
        offset: Int = 0
    ): List<JikanSearchResult> {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val previous = if (month == 1) (year - 1) to 12 else year to (month - 1)
        val current = seasonFor(year, month, limit, context, offset)
        if (offset > 0) return current
        if (current.size >= limit) return current.take(limit)
        val older = seasonFor(previous.first, previous.second, limit - current.size, context)
        return (current + older).distinctBy { it.malId }.take(limit)
    }

    /** "Yaklaşan" — gelecek ayın条目'ları. */
    suspend fun upcomingAnime(
        limit: Int = 20,
        context: Context? = null,
        offset: Int = 0
    ): List<JikanSearchResult> {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val next = if (month == 12) (year + 1) to 1 else year to (month + 1)
        return seasonFor(next.first, next.second, limit, context, offset)
    }

    /** "Anime Filmleri" — `cat=3` (Movie) alt kategorisi. */
    suspend fun movieAnime(limit: Int = 20, context: Context? = null, offset: Int = 0): List<JikanSearchResult> =
        runCatching {
            BangumiApiClient.browseSubjects(
                type = BangumiApiClient.SubjectType.ANIME,
                token = tokenOrNull(context),
                cat = BangumiApiClient.Category.ANIME_MOVIE,
                sort = "rank",
                limit = limit,
                offset = offset
            ).data.map { it.toSearchResult(MediaType.Movie) }.withLatinBangumiNames(context)
        }.getOrElse { logAndEmpty("movieAnime", it) }

    /**
     * `type = 6` (三次元) içinde **popüler** dizileri/filmleri getirir.
     *
     * Neden göz atma ucu (`GET /v0/subjects?type=6&cat=6001|6002&sort=rank`) yetmiyor?
     *  - REAL kataloğunda sıralamaya girmiş kayıt sayısı çok küçük (2026-10-31 canlı API:
     *    `cat=6001` toplam **51** kayıt) ve `sort=rank` sırası ağırlıklı olarak eski/niş
     *    yapıtları öne çıkarıyordu. Keşfet'teki "Bangumi Dizileri/Filmleri" rafı bu yüzden
     *    tanınmayan içerikle doluyor ya da boş kalıyordu.
     *  - Arama ucu `meta_tags` süzgecini ve `heat` (koleksiyona ekleme) sıralamasını
     *    destekliyor; böylece gerçekten popüler diziler/filmler öne geliyor.
     *
     * Arama ucu boş dönerse ya da hata verirse eski göz atma davranışına düşülür.
     */
    private suspend fun popularReal(
        metaTag: String,
        category: String,
        mediaType: MediaType,
        limit: Int,
        context: Context?,
        offset: Int
    ): List<JikanSearchResult> {
        val byHeat = runCatching {
            BangumiApiClient.searchSubjects(
                keyword = "",
                token = tokenOrNull(context),
                types = listOf(BangumiApiClient.SubjectType.REAL),
                sort = BangumiApiClient.SearchSort.HEAT,
                metaTags = listOf(metaTag),
                nsfw = false,
                limit = limit.coerceAtMost(BangumiApiClient.SEARCH_PAGE_SIZE),
                offset = offset
            ).data
        }.getOrElse { error ->
            Log.w(TAG, "popularReal($metaTag) araması başarısız: ${error.message}")
            emptyList()
        }
        val subjects = byHeat.ifEmpty {
            runCatching {
                BangumiApiClient.browseSubjects(
                    type = BangumiApiClient.SubjectType.REAL,
                    token = tokenOrNull(context),
                    cat = category,
                    sort = "rank",
                    limit = limit,
                    offset = offset
                ).data
            }.getOrElse { error ->
                Log.e(TAG, "Bangumi popularReal($metaTag)/browse failed: ${error.message}", error)
                emptyList()
            }
        }
        return subjects
            .filter { it.type == BangumiApiClient.SubjectType.REAL }
            .map { it.toSearchResult(mediaType) }
            .withLatinBangumiNames(context)
    }

    /** Bangumi REAL / 三次元 TV-drama şeridi — popülerlik sıralı (`meta_tags = 电视剧`). */
    suspend fun realTvShows(limit: Int = 20, context: Context? = null, offset: Int = 0): List<JikanSearchResult> =
        runCatching {
            popularReal(
                metaTag = "电视剧",
                category = BangumiApiClient.Category.REAL_TV,
                mediaType = MediaType.TvShow,
                limit = limit,
                context = context,
                offset = offset
            )
        }.getOrElse { logAndEmpty("realTvShows", it) }

    /** Bangumi REAL film şeridi — popülerlik sıralı (`meta_tags = 电影`), anime filmlerinden ayrı. */
    suspend fun realMovies(limit: Int = 20, context: Context? = null, offset: Int = 0): List<JikanSearchResult> =
        runCatching {
            popularReal(
                metaTag = "电影",
                category = BangumiApiClient.Category.REAL_MOVIE,
                mediaType = MediaType.Movie,
                limit = limit,
                context = context,
                offset = offset
            )
        }.getOrElse { logAndEmpty("realMovies", it) }

    /** "Yeni Eklenen Animeler" — yayın tarihine göre en yeni条目'lar. */
    suspend fun newlyAddedAnime(limit: Int = 20, context: Context? = null, offset: Int = 0): List<JikanSearchResult> =
        runCatching {
            BangumiApiClient.browseSubjects(
                type = BangumiApiClient.SubjectType.ANIME,
                token = tokenOrNull(context),
                sort = "date",
                limit = limit,
                offset = offset
            ).data.map { it.toSearchResult(MediaType.Anime) }.withLatinBangumiNames(context)
        }.getOrElse { logAndEmpty("newlyAddedAnime", it) }

    /**
     * "Yeni Eklenen Mangalar" — 书籍 (manga) kataloğunda yayın tarihine göre en yeniler.
     *
     * Keşfet'in Bangumi bölümü bu şeridi de çiziyor; eksik olduğu için raf her zaman
     * boş kalıyordu (bkz. `AllSourcesExplore.sourceSections` → `NEWLY_ADDED_MANGA`).
     */
    suspend fun newlyAddedManga(limit: Int = 20, context: Context? = null, offset: Int = 0): List<JikanSearchResult> =
        runCatching {
            BangumiApiClient.browseSubjects(
                type = BangumiApiClient.SubjectType.BOOK,
                token = tokenOrNull(context),
                cat = BangumiApiClient.Category.BOOK_COMIC,
                sort = "date",
                series = true,
                limit = limit,
                offset = offset
            ).data.map { it.toSearchResult(MediaType.Manga) }.withLatinBangumiNames(context)
        }.getOrElse { logAndEmpty("newlyAddedManga", it) }

    /**
     * "Noveller & Light Novel" — 书籍 kataloğunda `小说` (cat = 1002) sıralaması.
     *
     * Keşfet'in kaynak-özel bölüm listesi bu rafı her kaynak için çiziyor; Bangumi'de
     * (roman kataloğu binlerce kayıt olmasına rağmen) hiç doldurulmadığı için satır
     * sürekli boş kalıyordu.
     */
    /** "Noveller & Light Novel" rafı ve Tümünü Gör sayfası için alias. */
    suspend fun novelsShelf(limit: Int = 20, context: Context? = null, offset: Int = 0): List<JikanSearchResult> =
        novels(limit, context, offset)

    suspend fun novels(limit: Int = 20, context: Context? = null, offset: Int = 0): List<JikanSearchResult> =
        runCatching {
            BangumiApiClient.browseSubjects(
                type = BangumiApiClient.SubjectType.BOOK,
                token = tokenOrNull(context),
                cat = BangumiApiClient.Category.BOOK_NOVEL,
                series = true,
                sort = "rank",
                limit = limit,
                offset = offset
            ).data.map { it.toSearchResult(MediaType.Manga) }.withLatinBangumiNames(context)
        }.getOrElse { logAndEmpty("novels", it) }

    /** "En İyi Mangalar" — 书籍 kategori sıralaması. */
    suspend fun topManga(limit: Int = 20, context: Context? = null, offset: Int = 0): List<JikanSearchResult> =
        runCatching {
            // `cat=1001` (漫画) + `series=true`: 书籍 kataloğundaki roman/画集 kayıtları ve
            // cilt (单行本) kayıtları elenir, ızgarada seri ana kayıtları görünür.
            BangumiApiClient.topRanked(
                type = BangumiApiClient.SubjectType.BOOK,
                token = tokenOrNull(context),
                limit = limit,
                offset = offset,
                cat = BangumiApiClient.Category.BOOK_COMIC,
                series = true
            )
                .data.map { it.toSearchResult(MediaType.Manga) }.withLatinBangumiNames(context)
        }.getOrElse { logAndEmpty("topManga", it) }

    /** "Yayınlanan Mangalar" — en yeni书籍条目'ları. */
    suspend fun publishingManga(limit: Int = 20, context: Context? = null, offset: Int = 0): List<JikanSearchResult> =
        runCatching {
            BangumiApiClient.browseSubjects(
                type = BangumiApiClient.SubjectType.BOOK,
                token = tokenOrNull(context),
                cat = BangumiApiClient.Category.BOOK_COMIC,
                sort = "date",
                series = true,
                limit = limit,
                offset = offset
            ).data.map { it.toSearchResult(MediaType.Manga) }.withLatinBangumiNames(context)
        }.getOrElse { logAndEmpty("publishingManga", it) }

    /** "Popüler Mangalar" — koleksiyon sayısı sıralaması. */
    suspend fun trendingManga(limit: Int = 20, context: Context? = null, offset: Int = 0): List<JikanSearchResult> =
        runCatching {
            // `heat` sıralaması `cat` süzgecini desteklemez (v0'da `cat` yalnız göz atmada).
            // Bu yüzden 20'lik sayfa çekilip `platform = 漫画` olanlar süzülür; süzgeç sonrası
            // liste çok kısalırsa puan sıralamasına (cat=1001) düşülür ki şerit boş kalmasın.
            val raw = BangumiApiClient.mostCollected(
                type = BangumiApiClient.SubjectType.BOOK,
                token = tokenOrNull(context),
                limit = limit,
                offset = offset
            )
            val comics = raw.data
                .filter { it.type == BangumiApiClient.SubjectType.BOOK }
                .filter { it.platform == null || it.platform == "漫画" }
                .filter { it.series != false }
                .map { it.toSearchResult(MediaType.Manga) }
            if (comics.size >= 5) comics.withLatinBangumiNames(context)
            else topManga(limit, context, offset)
        }.getOrElse { logAndEmpty("trendingManga", it) }

    /** "En Yüksek Puanlı Mangalar" — bkz. [topRatedAnime] (oy tabanlı sıralama düzeltmesi). */
    suspend fun topRatedManga(limit: Int = 20, context: Context? = null, offset: Int = 0): List<JikanSearchResult> =
        runCatching {
            BangumiApiClient.topScored(
                type = BangumiApiClient.SubjectType.BOOK,
                token = tokenOrNull(context),
                limit = limit,
                offset = offset,
                minVotes = BangumiApiClient.MIN_RATING_VOTES,
                metaTags = listOf("漫画")
            ).data.map { it.toSearchResult(MediaType.Manga) }.withLatinBangumiNames(context)
                .ifEmpty { topManga(limit, context, offset) }
        }.getOrElse { logAndEmpty("topRatedManga", it) }

    /**
     * Keşfet "Tümü" modu ve tek kaynak modu için tüm şeritleri tek çağrıda toplar.
     * `ExploreViewModel.loadBangumiData()` bu fonksiyonu `ExplorePayload`'a çevirir.
     */
    suspend fun explorePayloadData(
        context: Context? = null,
        limit: Int = 20
    ): BangumiExploreData {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val next = if (month == 12) (year + 1) to 1 else year to (month + 1)
        return BangumiExploreData(
            topAnime = topAnime(limit, context),
            trendingAnime = trendingAnime(limit, context),
            topRatedAnime = topRatedAnime(limit, context),
            seasonalAnime = seasonFor(year, month, limit, context),
            airingAnime = airingAnime(limit, context),
            upcomingAnime = seasonFor(next.first, next.second, limit, context),
            movieAnime = movieAnime(limit, context),
            bangumiTvShows = realTvShows(limit, context),
            bangumiMovies = realMovies(limit, context),
            newlyAddedAnime = newlyAddedAnime(limit, context),
            newlyAddedManga = newlyAddedManga(limit, context),
            topManga = topManga(limit, context),
            publishingManga = publishingManga(limit, context),
            trendingManga = trendingManga(limit, context),
            topRatedManga = topRatedManga(limit, context)
        )
    }

    /** [explorePayloadData] çıktısı; `ExplorePayload` alan adlarıyla birebir hizalıdır. */
    data class BangumiExploreData(
        val topAnime: List<JikanSearchResult> = emptyList(),
        val trendingAnime: List<JikanSearchResult> = emptyList(),
        val topRatedAnime: List<JikanSearchResult> = emptyList(),
        val seasonalAnime: List<JikanSearchResult> = emptyList(),
        val airingAnime: List<JikanSearchResult> = emptyList(),
        val upcomingAnime: List<JikanSearchResult> = emptyList(),
        val movieAnime: List<JikanSearchResult> = emptyList(),
        val bangumiTvShows: List<JikanSearchResult> = emptyList(),
        val bangumiMovies: List<JikanSearchResult> = emptyList(),
        val newlyAddedAnime: List<JikanSearchResult> = emptyList(),
        val newlyAddedManga: List<JikanSearchResult> = emptyList(),
        val topManga: List<JikanSearchResult> = emptyList(),
        val publishingManga: List<JikanSearchResult> = emptyList(),
        val trendingManga: List<JikanSearchResult> = emptyList(),
        val topRatedManga: List<JikanSearchResult> = emptyList()
    )

    // ── Detay ────────────────────────────────────────────────────────────────

    /**
     * Bangumi条目 detayı. `subjectIdOrStable` hem gerçek `subject_id` hem de
     * Kitsugi stableId (500M+) olabilir.
     */
    suspend fun getSubject(subjectIdOrStable: Int, context: Context? = null): BangumiSubject? =
        withContext(Dispatchers.IO) {
            val subjectId = BangumiIdNamespace.rawIdFromStable(subjectIdOrStable) ?: subjectIdOrStable
            if (subjectId <= 0) return@withContext null
            runCatching {
                BangumiApiClient.getSubject(subjectId, tokenOrNull(context))
            }.getOrElse { error ->
                Log.e(TAG, "getSubject($subjectId) failed: ${error.message}")
                null
            }
        }

    /** Bölüm listesi (ana hikâye). Oyuncu/ilerleme eşlemesi için Bangumi bölüm ID'leri gerekir. */
    suspend fun getEpisodes(
        subjectIdOrStable: Int,
        context: Context? = null
    ): List<BangumiApiClient.BangumiEpisode> = withContext(Dispatchers.IO) {
        val subjectId = BangumiIdNamespace.rawIdFromStable(subjectIdOrStable) ?: subjectIdOrStable
        if (subjectId <= 0) return@withContext emptyList()
        runCatching {
            BangumiApiClient.getAllMainEpisodes(subjectId, tokenOrNull(context))
        }.getOrElse { error ->
            Log.e(TAG, "getEpisodes($subjectId) failed: ${error.message}")
            emptyList()
        }
    }

    /** İlişkili条目'lar: devam sezonu, ön hikâye, kitap uyarlaması. */
    suspend fun getRelated(subjectIdOrStable: Int, context: Context? = null):
        List<BangumiApiClient.BangumiRelatedSubject> = withContext(Dispatchers.IO) {
        val subjectId = BangumiIdNamespace.rawIdFromStable(subjectIdOrStable) ?: subjectIdOrStable
        runCatching {
            BangumiApiClient.getRelatedSubjects(subjectId, tokenOrNull(context))
        }.getOrElse { emptyList() }
    }

    // ── Model dönüşümü ───────────────────────────────────────────────────────

    /**
     * Bangumi条目 → [JikanSearchResult].
     *
     * Alan eşlemeleri:
     *  - `malId`      → Bangumi **stableId** (`subject_id + 500_000_000`)
     *  - `title`      → `name_cn` varsa o, yoksa `name` (Aniyomi/Mihon ile aynı tercih)
     *  - `titleJapanese` → `name` (Bangumi'de条目 adı genelde Japonca orijinaldir)
     *  - `score`      → `rating.score` (0-10, yuvarlanmış)
     *  - `rawScoreDouble` → ham puan (0.1 hassasiyetli gösterim için)
     *  - `rank`       → kategori içi sıra
     *  - `members`    → puanlayan kişi sayısı
     *  - `favorites`  → koleksiyon toplamı (收藏人数)
     *  - `total`      → `eps` (kitaplarda 话数)
     *  - `isAdult`    → `nsfw`
     *  - `subtitle`   → platform (TV / 剧场版 / WEB / 漫画 ...)
     */
    fun BangumiSubject.toSearchResult(preferredType: MediaType? = null): JikanSearchResult {
        val resolvedType = BangumiIdNamespace.mediaTypeFor(type, platform)
        val mediaType = when {
            preferredType == MediaType.Movie && type == BangumiApiClient.SubjectType.REAL -> MediaType.Movie
            preferredType == MediaType.Movie && type == BangumiApiClient.SubjectType.ANIME &&
                BangumiIdNamespace.isMoviePlatform(platform) -> MediaType.Movie
            preferredType == MediaType.Movie && resolvedType == MediaType.TvShow -> MediaType.Movie
            else -> resolvedType
        }
        val ratingScore = rating?.score ?: 0.0
        val collectionTotal = collection?.total ?: 0
        val localizedTitle = BangumiNameLocalizer.subject(name, nameCn, infobox)
        // Keşfet/arama kısa subject yanıtı infobox dönürmez. Önbellekteki İngilizce ile
        // romaji'yi ayrı alanlarda tut; tek bir genel Latin başlığı English diye kopyalama.
        val cachedTitles = BangumiTitleCache.get(id)
        val cachedEnglish = cachedTitles?.english?.takeIf {
            BangumiSubjectTitleResolver.hasReliableEnglish(cachedTitles)
        }
        val latinOverride = BangumiTitleCache.latinTitleFor(id, localizedTitle.display)
        return JikanSearchResult(
            malId = BangumiIdNamespace.stableIdFromRaw(id) ?: id,
            title = latinOverride ?: localizedTitle.display,
            subtitle = displayPlatformLabel(platform, type, mediaType),
            type = mediaType,
            total = eps.takeIf { it > 0 } ?: totalEpisodes.takeIf { it > 0 },
            score = if (ratingScore > 0) kotlin.math.round(ratingScore).toInt().coerceIn(0, 10) else null,
            isAdult = nsfw,
            imageUrl = BangumiApiClient.absoluteImageUrl(images?.poster),
            year = date?.take(4)?.toIntOrNull(),
            source = SOURCE,
            titleEnglish = localizedTitle.english ?: cachedEnglish,
            titleJapanese = localizedTitle.native ?: cachedTitles?.native,
            titleRomaji = localizedTitle.romaji ?: cachedTitles?.romaji ?: latinOverride,
            // Bangumi API yalnızca dikey kapak (poster) döndürür; yatay arka plan TMDB/Fanart'tan çözülür.
            backdropUrl = null,
            rank = rating?.rank?.takeIf { it > 0 },
            members = rating?.total?.takeIf { it > 0 },
            favorites = collectionTotal.takeIf { it > 0 },
            rawScoreDouble = ratingScore.takeIf { it > 0 },
            genres = (tags.ifEmpty { metaTags }).take(12),
            bangumiSubjectType = type
        )
    }

    /** Koleksiyon satırındaki kısaltılmış条目 → [JikanSearchResult] (içe aktarma önizlemesi). */
    fun BangumiApiClient.BangumiSlimSubject.toSearchResult(): JikanSearchResult {
        val localizedTitle = BangumiNameLocalizer.entity(name, nameCn)
        val cachedTitles = BangumiTitleCache.get(id)
        val cachedEnglish = cachedTitles?.english?.takeIf {
            BangumiSubjectTitleResolver.hasReliableEnglish(cachedTitles)
        }
        val latinOverride = BangumiTitleCache.latinTitleFor(id, localizedTitle.display)
        return JikanSearchResult(
            malId = BangumiIdNamespace.stableIdFromRaw(id) ?: id,
            title = latinOverride ?: localizedTitle.display,
            subtitle = subjectTypeLabel(type).uppercase(),
            type = BangumiIdNamespace.mediaTypeFor(type, platform),
            total = eps.takeIf { it > 0 },
            score = if (score > 0) kotlin.math.round(score).toInt().coerceIn(0, 10) else null,
            isAdult = false,
            imageUrl = BangumiApiClient.absoluteImageUrl(images?.poster),
            year = date?.take(4)?.toIntOrNull(),
            source = SOURCE,
            titleEnglish = localizedTitle.english ?: cachedEnglish,
            titleJapanese = localizedTitle.native ?: cachedTitles?.native,
            titleRomaji = localizedTitle.romaji ?: cachedTitles?.romaji ?: latinOverride,
            rank = rank.takeIf { it > 0 },
            favorites = collectionTotal.takeIf { it > 0 },
            rawScoreDouble = score.takeIf { it > 0 },
            genres = tags.take(12),
            bangumiSubjectType = type
        )
    }

    /** Karakter/kişi arama sonucu → [JikanSearchResult] (kimlik + ad + görsel yeterli). */
    private fun BangumiApiClient.BangumiEntity.toSearchResult(subtitle: String): JikanSearchResult {
        val localizedName = BangumiNameLocalizer.entity(name, nameCn)
        return JikanSearchResult(
            malId = id,
            title = localizedName.display,
            subtitle = subtitle,
            type = MediaType.Anime,
            total = null,
            score = null,
            isAdult = false,
            imageUrl = BangumiApiClient.absoluteImageUrl(images?.poster),
            year = null,
            source = SOURCE,
            titleEnglish = localizedName.english,
            titleJapanese = localizedName.native,
            titleRomaji = localizedName.romaji
        )
    }

    private fun displayPlatformLabel(platform: String?, subjectType: Int, mediaType: MediaType): String {
        if (subjectType == BangumiApiClient.SubjectType.REAL) {
            return if (mediaType == MediaType.Movie) "FİLM" else "DİZİ"
        }
        val value = platform?.trim().orEmpty()
        if (value.isBlank()) return subjectTypeLabel(subjectType).uppercase()
        return when (value) {
            "剧场版", "電影", "电影", "映画" -> "FİLM"
            "电视剧", "電視劇", "连续剧", "連續劇" -> "DİZİ"
            "漫画", "漫畫" -> "MANGA"
            else -> value.takeUnless { PreferenceHelpers.hasCjkCharacters(it) }?.uppercase()
                ?: subjectTypeLabel(subjectType).uppercase()
        }
    }

    private fun subjectTypeLabel(type: Int): String = when (type) {
        BangumiApiClient.SubjectType.BOOK -> "Kitap"
        BangumiApiClient.SubjectType.ANIME -> "Anime"
        BangumiApiClient.SubjectType.MUSIC -> "Müzik"
        BangumiApiClient.SubjectType.GAME -> "Oyun"
        BangumiApiClient.SubjectType.REAL -> "Dizi"
        else -> "Diğer"
    }

    private fun logAndEmpty(label: String, error: Throwable): List<JikanSearchResult> {
        if (error is CancellationException) throw error
        Log.e(TAG, "Bangumi $label failed: ${error.message}", error)
        return emptyList()
    }

    /** `https://bgm.tv/subject/{id}` — detay sayfasındaki "Kaynakta Aç" bağlantısı. */
    fun webUrlFor(subjectIdOrStable: Int): String {
        val subjectId = BangumiIdNamespace.rawIdFromStable(subjectIdOrStable) ?: subjectIdOrStable
        return "https://bgm.tv/subject/$subjectId"
    }
}

/**
 * Mevsim → Bangumi `air_date` çeyrek aralığı. Örn. `("SPRING", 2025)` → `>=2025-04-01` …
 * `<2025-07-01` (üst sınır dışlayıcı; canlı prob V1/V3/V5 ile doğrulandı).
 */
internal fun bangumiSeasonRange(season: String?, seasonYear: Int?): Pair<String, String>? {
    val year = seasonYear?.takeIf { it > 1900 } ?: return null
    val startMonth = when (season?.trim()?.uppercase(java.util.Locale.ROOT)) {
        "WINTER" -> 1
        "SPRING" -> 4
        "SUMMER" -> 7
        "FALL", "AUTUMN" -> 10
        else -> return null
    }
    val endYear = if (startMonth == 10) year + 1 else year
    val endMonth = if (startMonth == 10) 1 else startMonth + 3
    fun two(v: Int) = v.toString().padStart(2, '0')
    return "$year-${two(startMonth)}-01" to "$endYear-${two(endMonth)}-01"
}
