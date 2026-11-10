package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.core.memory.BoundedCache
import com.kitsugi.animelist.data.auth.BangumiApiClient
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.utils.PreferenceHelpers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Bangumi kişi/karakter detay listelerindeki **yapım başlıkları** için ortak Latin
 * (romaji / İngilizce) başlık çözümleyicisi.
 *
 * Bangumi'nin liste ve arama uçları çoğunlukla özgün adı (`name`, genellikle Japonca) ile
 * `name_cn` (Çince) döndürür; English alanı veya subject infobox'u bulunmayabilir. Bu nedenle
 * Keşfet, arama, profil ve kütüphane satırlarında tercih edilen dil için ortak çözüm uygular:
 *
 *  1. [BangumiTitleCache] — kayıt daha önce çözüldüyse ağ isteği yapılmaz; ancak Romaji tek
 *     başına güvenilir İngilizce sayılmaz.
 *  2. Anime/Manga için **AniList toplu araması** — bir GraphQL isteğinde birden çok aliased
 *     `Page(media(search: ...))` sorgusu gönderilir. Adayın `title.native` / `synonyms`
 *     alanlarından biri Bangumi özgün adıyla BİREBİR (normalize) eşleşirse adlar alınır.
 *  3. Bangumi v0 subject infobox'u (`英文名` / `罗马字` / `别名`) — önceki adı çözmediyse,
 *     sınırlı ve önbellekli subject detayı.
 *  4. Canlı-aksiyon içerik için TMDB — başlık, yıl ve medya türü doğrulanmadan eşleşme alınmaz.
 *
 * Tüm sonuçlar [BangumiTitleCache]'e kalıcı yazılır. Olumsuz önbelleğe yalnızca içerik için
 * uygun olan tüm kaynaklar gerçekten denendiyse ve güvenilir İngilizce bulunamadıysa girilir;
 * timeout, ağ hatası veya işlem sınırının dışındaki kayıtlar daha sonra yeniden denenebilir.
 */
internal object BangumiSubjectTitleResolver {

    private const val TAG = "BangumiTitleResolver"

    /** Bangumi subject kimliği + özgün başlık + görüntü türü ve biliniyorsa Bangumi türü. */
    internal data class Request(
        val rawId: Int,
        val nativeTitle: String,
        val mediaType: MediaType,
        val subjectType: Int? = null
    ) {
        companion object {
            /** Liste satırından istek üretir; stableId veya doğrudan rawId kabul edilir, geçersizse null. */
            fun fromRow(
                mediaId: Int,
                currentTitle: String,
                mediaTypeKey: String,
                subjectType: Int? = null
            ): Request? {
                val raw = BangumiIdNamespace.rawIdOrNull(mediaId) ?: return null
                return Request(raw, currentTitle, mediaTypeFromKey(mediaTypeKey), subjectType)
            }
        }
    }

    /** Romaji tek başına İngilizce tercihini tamamlamaz; arama ancak English gerçekten ayrışmışsa biter. */
    internal fun hasReliableEnglish(titles: BangumiTitleCache.LatinTitles?): Boolean {
        val english = usable(titles?.english) ?: return false
        val romaji = usable(titles?.romaji)
        return romaji == null || !english.equals(romaji, ignoreCase = true)
    }

    internal fun shouldTryAniList(request: Request): Boolean = when (request.subjectType) {
        BangumiApiClient.SubjectType.ANIME, BangumiApiClient.SubjectType.BOOK -> true
        BangumiApiClient.SubjectType.REAL -> false
        else -> request.mediaType == MediaType.Anime || request.mediaType == MediaType.Manga ||
            request.mediaType == MediaType.Movie
    }

    internal fun shouldTryTmdb(request: Request): Boolean = when (request.subjectType) {
        BangumiApiClient.SubjectType.ANIME, BangumiApiClient.SubjectType.BOOK -> false
        else -> request.mediaType == MediaType.TvShow || request.mediaType == MediaType.Movie
    }

    /** Negatif önbellek yalnızca gereken her provider pass'i tamamlandıysa yazılabilir. */
    internal fun shouldMarkNegative(
        request: Request,
        completedAniList: Boolean,
        completedTmdb: Boolean,
        titles: BangumiTitleCache.LatinTitles?
    ): Boolean {
        if (hasReliableEnglish(titles)) return false
        val needsAniList = shouldTryAniList(request)
        val needsTmdb = shouldTryTmdb(request)
        return (needsAniList || needsTmdb) &&
            (!needsAniList || completedAniList) &&
            (!needsTmdb || completedTmdb)
    }

    internal fun mediaTypeFromKey(key: String): MediaType = when (key.trim().lowercase(Locale.ROOT)) {
        "manga" -> MediaType.Manga
        "movie" -> MediaType.Movie
        "tv", "tvshow" -> MediaType.TvShow
        else -> MediaType.Anime
    }

    private const val INFOBOX_PASS_LIMIT = 24
    private const val INFOBOX_PASS_LIMIT_BACKGROUND = 16
    private const val ANILIST_PASS_LIMIT = 24
    private const val ANILIST_PASS_LIMIT_BACKGROUND = 48
    private const val ANILIST_BATCH_SIZE = 10

    /**
     * Liste modu (Keşfet şeritleri, arama ızgarası, kütüphane): yalnızca önbellek + **tek**
     * AniList geçişi. Infobox/TMDB geçişleri kapatılır.
     *
     * Neden: p1 liste uçları infobox döndürmediği için her satır tek tek `/v0/subjects/{id}`
     * ile çözülüyordu. Keşfet 14 Bangumi şeridini aynı anda açtığında bu, şerit başına 40,
     * toplamda 500'den fazla istek demekti. Bangumi'nin jeton kovalı sınırlayıcısıyla
     * birleşince veri dakikalarca gelmiyordu. AniList toplu araması aynı işi 2 istekte
     * yapar; bulunamayan satırlar özgün (CJK) adıyla gösterilir ve detay sayfası açıldığında
     * kalıcı önbelleğe yazılır.
     */
    private const val LIST_ANILIST_LIMIT = 20
    private const val LIST_ANILIST_BATCH_TIMEOUT_MS = 5_000L
    /** Detay sekmesi (ilişkiler/öneriler/yapımlar): daha çok satır, infobox yedeği de açık. */
    private const val TAB_ANILIST_LIMIT = 64
    private const val TAB_ANILIST_BATCH_TIMEOUT_MS = 6_000L
    /**
     * Detay sekmesi infobox yedeği. İlişki/öneri listeleri 20-40 satıra çıkabildiği için
     * 12'lik eski tavan listenin yarısını Japonca bırakıyordu. AniList geçişiyle **paralel**
     * çalıştığı için (bkz. [resolveTabMode]) ek gecikme yaratmaz; tavan yine de sınırlı
     * tutulur ki 100 satırlık listelerde yüzlerce `/v0/subjects` isteği doğmasın.
     */
    private const val TAB_INFOBOX_LIMIT = 32
    private const val TAB_INFOBOX_BATCH_TIMEOUT_MS = 8_000L
    private const val INFOBOX_LOOKUP_TIMEOUT_MS = 3_500L
    private const val INFOBOX_BATCH_TIMEOUT_MS = 5_000L
    private const val INFOBOX_BATCH_TIMEOUT_BACKGROUND_MS = 6_000L
    private const val ANILIST_BATCH_TIMEOUT_MS = 9_000L
    private const val ANILIST_BATCH_TIMEOUT_BACKGROUND_MS = 8_000L
    private const val TMDB_PASS_LIMIT = 20
    private const val TMDB_PASS_LIMIT_BACKGROUND = 20
    private const val TMDB_BATCH_TIMEOUT_MS = 8_000L
    private const val TMDB_BATCH_TIMEOUT_BACKGROUND_MS = 10_000L
    private const val NEGATIVE_TTL_MS = 6 * 60 * 60 * 1000L

    private val infoboxSemaphore = Semaphore(8)
    private val anilistSemaphore = Semaphore(2)
    private val tmdbSemaphore = Semaphore(3)
    // Keşfet rafları aynı Bangumi subject'i eşzamanlı çözmek isteyebilir. Kısa stripe kilitleri
    // aynı ID için provider çağrılarını tekilleştirir; sabit boyutla sınırsız kilit birikmesi önlenir.
    private val resolutionLocks = Array(256) { Mutex() }

    /** Ağ isteği yapıldığı hâlde Latin ad bulunamayan kayıtlar (tekrar arama yapılmaz). */
    private val negativeCache = BoundedCache<Int, Long>("bangumi.titleResolver.negative", 512)

    /**
     * Verilen satırlar için Latin başlık çözer. Senkron geç kısa, arka plan (background)
     * geç uzun bütçeyle çalışır; ikisi de sonuçları kalıcı önbelleğe yazar.
     *
     * [cacheOnly] modunda yalnızca [BangumiTitleCache] okunur (ağ isteği YOK) — ilk çizimi
     * geciktirmemek için senkron istemci geçi bu modda çalışır; ağ işini arka plan geçi yapar.
     */
    suspend fun resolve(
        requests: List<Request>,
        background: Boolean = false,
        cacheOnly: Boolean = false,
        listMode: Boolean = false,
        tabMode: Boolean = false
    ): Map<Int, BangumiTitleCache.LatinTitles> {
        if (tabMode) return resolveTabMode(requests, cacheOnly)
        if (listMode) return resolveListMode(requests, cacheOnly)
        return withResolutionLocks(requests) {
            resolveLocked(requests, background, cacheOnly)
        }
    }

    /**
     * Detay sekmesi modu (Karakterler'in "Yapımlar" listesi, İlişkiler, Öneriler).
     *
     * Liste modundan farkı: tek seferde en fazla [TAB_ANILIST_LIMIT] satır AniList toplu
     * aramasıyla çözülür; ayrıca **paralel** olarak en fazla [TAB_INFOBOX_LIMIT] kayıt için
     * Bangumi v0 subject infobox'ı okunur. Infobox, AniList'in romaji-only sonucunu gerçek
     * İngilizce adla değiştirebilen tek kaynaktır. İki geçiş paralel olduğu için süreler
     * toplanmaz; tavan yine de sınırlıdır (100 satırlık listelerde yüzlerce istek doğmasın).
     */
    private suspend fun resolveTabMode(
        requests: List<Request>,
        cacheOnly: Boolean
    ): Map<Int, BangumiTitleCache.LatinTitles> {
        val result = ConcurrentHashMap<Int, BangumiTitleCache.LatinTitles>()
        val pending = mutableListOf<Request>()
        for (req in requests.filter { it.rawId > 0 }.distinctBy { it.rawId }) {
            val cached = usableLatin(BangumiTitleCache.get(req.rawId))
            if (cached != null) result[req.rawId] = cached
            if (!hasReliableEnglish(cached) && !isNegative(req.rawId)) pending += req
        }
        if (pending.isEmpty() || cacheOnly) return result

        val anilistPending = pending
            .filter { shouldTryAniList(it) && PreferenceHelpers.hasCjkCharacters(it.nativeTitle) }
            .take(TAB_ANILIST_LIMIT)
        val infoboxPending = pending
            .filter { !isNegative(it.rawId) }
            .take(TAB_INFOBOX_LIMIT)

        // İki geçiş PARALEL: AniList toplu araması romaji verir, Bangumi infobox'ı gerçek
        // İngilizce adı verir. Sırayla çalıştıklarında süreler toplanıyor (6 s + 8 s) ve
        // sekme zenginleştirme bütçesi dolup liste Japonca kalıyordu.
        coroutineScope {
            launch { runAniListPass(anilistPending, TAB_ANILIST_BATCH_TIMEOUT_MS, result) }
            launch { runInfoboxPass(infoboxPending, TAB_INFOBOX_BATCH_TIMEOUT_MS, result) }
        }

        // Geçişler birbirinin çözdüğü kayıtları önbellekten görsün: eşzamanlı iki pass'in
        // sonuçlarını tek haritada birleştir (tamamlanmış olanlar tekrar istenmez).
        for (req in requests) {
            if (result.containsKey(req.rawId)) continue
            usableLatin(BangumiTitleCache.get(req.rawId))?.let { result[req.rawId] = it }
        }
        return result
    }

    /** Ortak AniList geçişi: aliased toplu arama + sonuçları kalıcı önbelleğe yazma. */
    private suspend fun runAniListPass(
        requests: List<Request>,
        timeoutMs: Long,
        result: MutableMap<Int, BangumiTitleCache.LatinTitles>
    ) {
        if (requests.isEmpty()) return
        withTimeoutOrNull(timeoutMs) {
            coroutineScope {
                requests.chunked(ANILIST_BATCH_SIZE).map { chunk ->
                    async(Dispatchers.IO) {
                        try {
                            anilistSemaphore.withPermit { resolveChunkViaAniList(chunk) }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (e: Exception) {
                            android.util.Log.w(TAG, "AniList toplu araması başarısız: ${e.message}")
                            null
                        }
                    }
                }.awaitAll().forEach { chunkResult ->
                    if (chunkResult == null) return@forEach
                    val appCtx = com.kitsugi.animelist.KitsugiApplication.getInstance()?.applicationContext
                    for ((rawId, latin) in chunkResult.resolved) {
                        BangumiTitleCache.put(rawId, latin.romaji, latin.english, latin.native)
                        result[rawId] = usableLatin(BangumiTitleCache.get(rawId)) ?: latin
                        val malId = chunkResult.matchedMalIds[rawId]
                        val req = requests.firstOrNull { it.rawId == rawId }
                        if (appCtx != null && malId != null && malId > 0 && req != null) {
                            val resolvedSubjectType = req.subjectType
                                ?: BangumiIdNamespace.subjectTypeFor(req.mediaType)
                            runCatching {
                                BangumiLocalMappingCache.put(appCtx, malId, resolvedSubjectType, rawId)
                            }
                        }
                    }
                }
            }
        }
    }

    /** Ortak infobox geçişi: yalnızca hâlâ Latin adı olmayan kayıtlar; sonuç kalıcıdır. */
    private suspend fun runInfoboxPass(
        requests: List<Request>,
        timeoutMs: Long,
        result: MutableMap<Int, BangumiTitleCache.LatinTitles>
    ) {
        if (requests.isEmpty()) return
        val loadedSubjects = ConcurrentHashMap<Int, BangumiApiClient.BangumiSubject>()
        withTimeoutOrNull(timeoutMs) {
            coroutineScope {
                requests.map { req ->
                    async(Dispatchers.IO) {
                        try {
                            infoboxSemaphore.withPermit {
                                val localized = loadLocalizedSubject(req, loadedSubjects) ?: return@async
                                val latin = latinOf(localized.romaji, localized.english, localized.native)
                                    ?: return@async
                                BangumiTitleCache.put(req.rawId, latin.romaji, latin.english, latin.native)
                                result[req.rawId] = usableLatin(BangumiTitleCache.get(req.rawId)) ?: latin
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            null
                        }
                    }
                }.awaitAll()
            }
        }
    }

    /**
     * Liste ekranları için hafif çözümleme: önbellek + tek AniList geçişi.
     *
     * - Per-subject `/v0/subjects` (infobox) ve TMDB geçişleri **yapılmaz**; bunlar
     *   yüzlerce istek üretiyordu (bkz. [LIST_ANILIST_LIMIT] açıklaması).
     * - Kilit şeritleri (stripe lock) alınmaz: 14 şerit aynı anda açıldığında birbirini
     *   sıraya sokup toplam süreyi katlıyordu. Aynı ID'nin iki şeritte çözülmesi nadirdir ve
     *   [BangumiTitleCache] bunu zaten tekilleştirir.
     * - Olumsuz önbellek yazılmaz: infobox/TMDB denenmediği için "İngilizce yok" hükmü
     *   verilemez; detay sayfası sonraki açılışta tam çözümlemeyi yapabilir.
     */
    private suspend fun resolveListMode(
        requests: List<Request>,
        cacheOnly: Boolean
    ): Map<Int, BangumiTitleCache.LatinTitles> {
        val result = ConcurrentHashMap<Int, BangumiTitleCache.LatinTitles>()
        val pending = mutableListOf<Request>()
        for (req in requests.filter { it.rawId > 0 }.distinctBy { it.rawId }) {
            val cached = usableLatin(BangumiTitleCache.get(req.rawId))
            if (cached != null) result[req.rawId] = cached
            if (!hasReliableEnglish(cached) && !isNegative(req.rawId)) pending += req
        }
        if (pending.isEmpty() || cacheOnly) return result

        // Zaten Latin yazımlı başlıklar için ağ isteği yapılmaz: listede okunabilir bir ad
        // var; İngilizce olup olmadığı ayrımı yalnızca detay sayfasında (tam çözümleme) gerekir.
        val anilistPending = pending
            .filter { shouldTryAniList(it) && PreferenceHelpers.hasCjkCharacters(it.nativeTitle) }
            .take(LIST_ANILIST_LIMIT)
        runAniListPass(anilistPending, LIST_ANILIST_BATCH_TIMEOUT_MS, result)
        return result
    }

    private suspend fun resolveLocked(
        requests: List<Request>,
        background: Boolean,
        cacheOnly: Boolean
    ): Map<Int, BangumiTitleCache.LatinTitles> {
        val result = ConcurrentHashMap<Int, BangumiTitleCache.LatinTitles>()
        val pending = mutableListOf<Request>()
        for (req in requests.filter { it.rawId > 0 }.distinctBy { it.rawId }) {
            val cached = usableLatin(BangumiTitleCache.get(req.rawId))
            if (cached != null) result[req.rawId] = cached
            // Romaji-only cache ENGLISH seçimini tamamlamaz. Kısmi başlığı gösterirken
            // gerçek İngilizceyi başka kaynaktan tamamlamayı da dene.
            if (!hasReliableEnglish(cached) && !isNegative(req.rawId)) pending += req
        }
        if (pending.isEmpty() || cacheOnly) return result

        val loadedSubjects = ConcurrentHashMap<Int, BangumiApiClient.BangumiSubject>()

        // Kütüphane satırında Bangumi subjectType saklanmıyor. Movie türündeki eski satırlarda
        // anime filmi ile canlı-aksiyon filmi karışmasın diye source routing'den önce subject'i
        // yükleyip gerçek Bangumi türünü öğren.
        val typeProbePending = pending.filter { it.mediaType == MediaType.Movie && it.subjectType == null }
            .take(if (background) INFOBOX_PASS_LIMIT_BACKGROUND else INFOBOX_PASS_LIMIT)
        if (typeProbePending.isNotEmpty()) {
            val typeProbeTimeout = if (background) INFOBOX_BATCH_TIMEOUT_BACKGROUND_MS else INFOBOX_BATCH_TIMEOUT_MS
            withTimeoutOrNull(typeProbeTimeout) {
                coroutineScope {
                    typeProbePending.map { req ->
                        async(Dispatchers.IO) {
                            val localized = loadLocalizedSubject(req, loadedSubjects) ?: return@async
                            val latin = latinOf(localized.romaji, localized.english, localized.native) ?: return@async
                            BangumiTitleCache.put(req.rawId, latin.romaji, latin.english, latin.native)
                            result[req.rawId] = usableLatin(BangumiTitleCache.get(req.rawId)) ?: latin
                        }
                    }.awaitAll()
                }
            }
        }

        // 1) Önce Anime/Manga için Aliased AniList araması yap. Bu, infobox'u olmayan
        // Bangumi listelerindeki yaygın durumu tek istekte çözer; sağlam İngilizce bulunduğunda
        // aşağıdaki per-subject detay isteği tamamen atlanır.
        val anilistLimit = if (background) ANILIST_PASS_LIMIT_BACKGROUND else ANILIST_PASS_LIMIT
        val anilistBatchTimeout = if (background) ANILIST_BATCH_TIMEOUT_BACKGROUND_MS else ANILIST_BATCH_TIMEOUT_MS
        val anilistPending = pending.filter { req ->
            val actualType = loadedSubjects[req.rawId]?.type ?: req.subjectType
            shouldTryAniList(req.copy(subjectType = actualType)) && !isNegative(req.rawId) &&
                !hasReliableEnglish(result[req.rawId] ?: BangumiTitleCache.get(req.rawId))
        }.take(anilistLimit)
        val completedAniList = ConcurrentHashMap.newKeySet<Int>()
        if (anilistPending.isNotEmpty()) {
            withTimeoutOrNull(anilistBatchTimeout) {
                coroutineScope {
                    anilistPending.chunked(ANILIST_BATCH_SIZE).map { chunk ->
                        async(Dispatchers.IO) {
                            try {
                                anilistSemaphore.withPermit { resolveChunkViaAniList(chunk) }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (e: Exception) {
                                android.util.Log.w(TAG, "AniList başlık toplu araması başarısız: ${e.message}")
                                null
                            }
                        }
                    }.awaitAll().forEach { chunkResult ->
                        if (chunkResult == null) return@forEach
                        completedAniList.addAll(chunkResult.unmatched)
                        val appCtx = com.kitsugi.animelist.KitsugiApplication.getInstance()?.applicationContext
                        for ((rawId, latin) in chunkResult.resolved) {
                            completedAniList += rawId
                            BangumiTitleCache.put(rawId, latin.romaji, latin.english, latin.native)
                            result[rawId] = usableLatin(BangumiTitleCache.get(rawId)) ?: latin
                            val malId = chunkResult.matchedMalIds[rawId]
                            val req = anilistPending.firstOrNull { it.rawId == rawId }
                            if (appCtx != null && malId != null && malId > 0 && req != null) {
                                val resolvedSubjectType = req.subjectType
                                    ?: BangumiIdNamespace.subjectTypeFor(req.mediaType)
                                runCatching {
                                    BangumiLocalMappingCache.put(appCtx, malId, resolvedSubjectType, rawId)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2) English hâlâ doğrulanmadıysa subject infobox'una dön (infobox'a özgü English / romaji).
        // Burada "Latin ad bulunamadı" diye negative-cache yazılmaz: Anime/Manga yukarıda AniList'i,
        // canlı-aksiyon da aşağıda TMDB'yi henüz denememiş olabilir.
        val infoboxLimit = if (background) INFOBOX_PASS_LIMIT_BACKGROUND else INFOBOX_PASS_LIMIT
        val infoboxBatchTimeout = if (background) INFOBOX_BATCH_TIMEOUT_BACKGROUND_MS else INFOBOX_BATCH_TIMEOUT_MS
        val infoboxPending = pending.filter { req ->
            !isNegative(req.rawId) &&
                !hasReliableEnglish(result[req.rawId] ?: BangumiTitleCache.get(req.rawId))
        }.take(infoboxLimit)
        if (infoboxPending.isNotEmpty()) {
            withTimeoutOrNull(infoboxBatchTimeout) {
                coroutineScope {
                    infoboxPending.map { req ->
                        async(Dispatchers.IO) {
                            val localized = loadLocalizedSubject(req, loadedSubjects) ?: return@async
                            val latin = latinOf(localized.romaji, localized.english, localized.native)
                                ?: return@async
                            BangumiTitleCache.put(req.rawId, latin.romaji, latin.english, latin.native)
                            result[req.rawId] = usableLatin(BangumiTitleCache.get(req.rawId)) ?: latin
                        }
                    }.awaitAll()
                }
            }
        }
        // 3) Canlı-aksiyon (ve türü belirsiz film) adları için Bangumi'nin aynı katı TMDB
        // başlık/yıl/tür eşleştirmesi. AniList'e ait anime filmleri subjectType=ANIME ile bu
        // kola düşmez; böylece anime filmine yanlışlıkla canlı-aksiyon adı yazılmaz.
        val tmdbLimit = if (background) TMDB_PASS_LIMIT_BACKGROUND else TMDB_PASS_LIMIT
        val tmdbBatchTimeout = if (background) TMDB_BATCH_TIMEOUT_BACKGROUND_MS else TMDB_BATCH_TIMEOUT_MS
        val tmdbPending = pending.filter { req ->
            val actualType = loadedSubjects[req.rawId]?.type ?: req.subjectType
            shouldTryTmdb(req.copy(subjectType = actualType)) && !isNegative(req.rawId) &&
                !hasReliableEnglish(result[req.rawId] ?: BangumiTitleCache.get(req.rawId))
        }.take(tmdbLimit)
        val completedTmdb = ConcurrentHashMap.newKeySet<Int>()
        if (tmdbPending.isNotEmpty()) {
            withTimeoutOrNull(tmdbBatchTimeout) {
                coroutineScope {
                    tmdbPending.map { req ->
                        async(Dispatchers.IO) {
                            try {
                                val subject = loadedSubjects[req.rawId]
                                    ?: KitsugiBangumiDetailClient.loadSubject(req.rawId)
                                    ?: return@async
                                loadedSubjects[req.rawId] = subject
                                if (!shouldTryTmdb(req.copy(subjectType = subject.type))) return@async
                                val match = tmdbSemaphore.withPermit {
                                    KitsugiBangumiDetailClient.findMatchingTmdbTitle(
                                        rawId = req.rawId,
                                        mediaType = req.mediaType,
                                        subjectHint = subject
                                    )
                                }
                                completedTmdb += req.rawId
                                if (match != null) {
                                    val english = usable(match.titleEnglish)
                                    val existing = result[req.rawId]
                                        ?: usableLatin(BangumiTitleCache.get(req.rawId))
                                    val tmdbTitle = usable(match.title)
                                    val romaji = usable(match.titleRomaji)
                                        ?: existing?.romaji
                                        ?: tmdbTitle?.takeUnless { english != null && it.equals(english, true) }
                                    val titles = latinOf(romaji, english, existing?.native ?: req.nativeTitle)
                                    if (titles != null) {
                                        BangumiTitleCache.put(
                                            req.rawId,
                                            titles.romaji,
                                            titles.english,
                                            titles.native
                                        )
                                        result[req.rawId] = usableLatin(BangumiTitleCache.get(req.rawId)) ?: titles
                                    }
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (e: Exception) {
                                android.util.Log.w(TAG, "TMDB başlık eşleştirmesi başarısız (${req.rawId}): ${e.message}")
                            }
                        }
                    }.awaitAll()
                }
            }
        }

        // Bir içerik ancak kendisi için uygun olan bütün dış kaynaklar denendiyse olumsuz
        // önbelleğe girer. Zaman aşımı/ağ hatası ve çözümleme limitinin dışındaki kayıtlar,
        // sonraki açılışta tekrar denenebilir.
        val anilistDoneIds = completedAniList.toSet()
        val tmdbDoneIds = completedTmdb.toSet()
        for (req in pending) {
            val current = result[req.rawId] ?: usableLatin(BangumiTitleCache.get(req.rawId))
            val effectiveRequest = req.copy(
                subjectType = loadedSubjects[req.rawId]?.type ?: req.subjectType
            )
            if (
                shouldMarkNegative(
                    request = effectiveRequest,
                    completedAniList = req.rawId in anilistDoneIds,
                    completedTmdb = req.rawId in tmdbDoneIds,
                    titles = current
                )
            ) {
                markNegative(req.rawId)
            }
        }
        return result
    }

    private suspend fun <T> withResolutionLocks(requests: List<Request>, block: suspend () -> T): T {
        // Her çağrı kilitleri ID sırasına göre alır; örtüşen raflar birbirini beklese de deadlock olmaz.
        val indexes = requests.asSequence()
            .map { it.rawId }
            .filter { it > 0 }
            .map { it % resolutionLocks.size }
            .distinct()
            .sorted()
            .toList()
        val acquired = mutableListOf<Int>()
        try {
            for (index in indexes) {
                resolutionLocks[index].lock()
                acquired += index
            }
            return block()
        } finally {
            acquired.asReversed().forEach { resolutionLocks[it].unlock() }
        }
    }

    private suspend fun loadLocalizedSubject(
        request: Request,
        loadedSubjects: ConcurrentHashMap<Int, BangumiApiClient.BangumiSubject>
    ): BangumiLocalizedName? = try {
        infoboxSemaphore.withPermit {
            withTimeoutOrNull(INFOBOX_LOOKUP_TIMEOUT_MS) {
                val subject = loadedSubjects[request.rawId]
                    ?: KitsugiBangumiDetailClient.loadSubject(request.rawId)
                    ?: return@withTimeoutOrNull null
                loadedSubjects[request.rawId] = subject
                BangumiNameLocalizer.subject(subject.name, subject.nameCn, subject.infobox)
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    // ── AniList aliased arama ─────────────────────────────────────────────────

    internal data class AniListChunkResult(
        val resolved: Map<Int, BangumiTitleCache.LatinTitles>,
        val unmatched: List<Int>,
        val matchedMalIds: Map<Int, Int> = emptyMap()
    )

    private suspend fun resolveChunkViaAniList(chunk: List<Request>): AniListChunkResult? {
        val (query, variables) = aliasedQuery(chunk)
        val response = KitsugiApiBase.executeAniListQuery(query, variables) ?: return null
        return matchAniListResponse(chunk, runCatching { JSONObject(response) }.getOrNull())
    }

    /** Tek istekte N aliased `Page(media(search:))` sorgusu kurar. */
    private fun aliasedQuery(chunk: List<Request>): Pair<String, JSONObject> {
        val sb = StringBuilder("query(")
        chunk.forEachIndexed { index, _ ->
            if (index > 0) sb.append(", ")
            sb.append("\$s").append(index).append(": String, \$t").append(index).append(": MediaType")
        }
        sb.append(") {\n")
        chunk.forEachIndexed { index, _ ->
            sb.append("a").append(index)
                .append(": Page(page: 1, perPage: 5) { media(search: \$s").append(index)
                .append(", type: \$t").append(index)
                .append(", sort: SEARCH_MATCH) { id idMal title { romaji english native } synonyms } }\n")
        }
        sb.append("}")
        val variables = JSONObject()
        chunk.forEachIndexed { index, req ->
            variables.put("s$index", req.nativeTitle)
            variables.put("t$index", if (req.mediaType == MediaType.Manga) "MANGA" else "ANIME")
        }
        return sb.toString() to variables
    }

    /**
     * AniList yanıtını birebir (normalize) başlık eşleşmesiyle doğrular. Yalnızca özgün
     * ad / romaji / İngilizce / synonyms alanlarından biri Bangumi başlığıyla aynıysa
     * kabul edilir — yanlış yapımın adı asla gösterilmez.
     */
    internal fun matchAniListResponse(chunk: List<Request>, response: JSONObject?): AniListChunkResult {
        val resolved = HashMap<Int, BangumiTitleCache.LatinTitles>()
        val matchedMalIds = HashMap<Int, Int>()
        val unmatched = mutableListOf<Int>()
        val data = response?.optJSONObject("data") ?: return AniListChunkResult(emptyMap(), emptyList())
        for ((index, req) in chunk.withIndex()) {
            val target = KitsugiBangumiDetailClient.normalizeTitle(req.nativeTitle)
            if (target.length < 2) {
                unmatched += req.rawId
                continue
            }
            val media = data.optJSONObject("a$index")?.optJSONArray("media").objects()
            var match: BangumiTitleCache.LatinTitles? = null
            var matchMalId: Int? = null
            for (m in media) {
                val title = m.optJSONObject("title") ?: continue
                val candidates = buildList {
                    add(title.strOrNull("native"))
                    add(title.strOrNull("romaji"))
                    add(title.strOrNull("english"))
                    m.optJSONArray("synonyms")?.let { arr ->
                        for (j in 0 until arr.length()) add(arr.strOrNull(j))
                    }
                }
                val hit = candidates.any { candidate ->
                    val normalized = KitsugiBangumiDetailClient.normalizeTitle(candidate)
                    normalized.length >= 2 && normalized == target
                }
                if (hit) {
                    match = latinOf(
                        title.strOrNull("romaji"),
                        title.strOrNull("english"),
                        title.strOrNull("native")
                    )
                    matchMalId = m.optInt("idMal", 0).takeIf { it > 0 }
                    break
                }
            }
            if (match != null) {
                resolved[req.rawId] = match
                if (matchMalId != null) matchedMalIds[req.rawId] = matchMalId
            } else {
                unmatched += req.rawId
            }
        }
        return AniListChunkResult(resolved, unmatched, matchedMalIds)
    }

    // ── Yardımcılar ───────────────────────────────────────────────────────────

    /** Latin (kullanılabilir) romaji/İngilizce içeren önbellek girdisi; yoksa null. */
    private fun usableLatin(titles: BangumiTitleCache.LatinTitles?): BangumiTitleCache.LatinTitles? {
        if (titles == null) return null
        val romaji = usable(titles.romaji)
        val english = usable(titles.english)
        if (romaji == null && english == null) return null
        return BangumiTitleCache.LatinTitles(romaji, english, titles.native)
    }

    private fun latinOf(romaji: String?, english: String?, native: String?): BangumiTitleCache.LatinTitles? {
        val r = usable(romaji)
        val e = usable(english)
        if (r == null && e == null) return null
        return BangumiTitleCache.LatinTitles(r, e, native?.trim()?.takeIf { it.isNotEmpty() })
    }

    private fun usable(value: String?): String? = value?.trim()?.takeIf {
        it.isNotEmpty() && PreferenceHelpers.isLatinText(it)
    }

    private fun isNegative(rawId: Int): Boolean {
        val at = negativeCache[rawId] ?: return false
        return System.currentTimeMillis() - at < NEGATIVE_TTL_MS
    }

    private fun markNegative(rawId: Int) {
        negativeCache[rawId] = System.currentTimeMillis()
    }

    private fun JSONObject.strOrNull(key: String): String? =
        if (isNull(key)) null else optString(key, "").trim().takeIf { it.isNotEmpty() }

    private fun org.json.JSONArray?.objects(): List<JSONObject> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optJSONObject(it) }
    }

    private fun org.json.JSONArray?.strOrNull(index: Int): String? {
        if (this == null || index < 0 || index >= length()) return null
        return optString(index, "").trim().takeIf { it.isNotEmpty() }
    }
}
