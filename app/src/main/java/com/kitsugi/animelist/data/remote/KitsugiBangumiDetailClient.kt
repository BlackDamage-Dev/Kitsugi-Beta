package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.core.memory.BoundedCache

import android.util.Log
import com.kitsugi.animelist.KitsugiApplication
import com.kitsugi.animelist.data.auth.BangumiApiClient
import com.kitsugi.animelist.data.auth.BangumiApiClient.BangumiSubject
import com.kitsugi.animelist.data.auth.BangumiAuthStore
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.utils.KitsugiSpoilerRules
import com.kitsugi.animelist.utils.toLatinStudioName
import com.kitsugi.animelist.utils.toLocalizedOrgName
import com.kitsugi.animelist.utils.localizedDistinctTags
import com.kitsugi.animelist.utils.toLocalizedTagLabel
import com.kitsugi.animelist.utils.toTurkishBroadcast
import com.kitsugi.animelist.utils.isTurkish
import com.kitsugi.animelist.utils.toTurkishCharacterRole
import com.kitsugi.animelist.utils.toTurkishDuration
import com.kitsugi.animelist.utils.toTurkishRelationType
import com.kitsugi.animelist.utils.toTurkishSeason
import com.kitsugi.animelist.utils.toTurkishStaffRole
import com.kitsugi.animelist.utils.toLocalizedStaffRole
import com.kitsugi.animelist.utils.BangumiOrgDictionary
import com.kitsugi.animelist.utils.toTurkishStatus
import com.kitsugi.animelist.utils.PreferenceHelpers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.R

/**
 * Bangumi (bgm.tv) **detay sayfası** veri katmanı.
 *
 * Bangumi kayıtları (`source = "bangumi"`, `malId` = stableId = subject_id + 500_000_000) eskiden
 * detay hattında hiçbir dala girmiyor; ana detay Kitsu başlık aramasına, sekmeler (karakter, ekip,
 * öneri, ilişki, istatistik, yorum, bölüm) ise boş listeye düşüyordu. Bu nesne:
 *
 *  1. **Bangumi-yerel veriyi** çeker (v0 `/subjects/{id}` + web arayüzünün `next.bgm.tv/p1` uçları:
 *     karakterler + seslendirmenler, ekip, ilişkiler, öneriler, yorumlar/incelemeler, bölümler),
 *  2. Kaydı **diğer platformlarla eşler** (AniList arama → MAL id → ARM → TMDB/Kitsu/TVDB/IMDb;
 *     sonuçlar kalıcı olarak önbelleğe alınır) ve
 *  3. MAL / AniList / TMDB verisini **birleştirir**: Bangumi alanları (başlık, puan, sıra) korunur,
 *     eksikler (tür, yaş sınırı, açılış/kapanış müzikleri, fragman, harici bağlantılar ...) diğer
 *     kaynaklardan tamamlanır. Galeri tarafı (Fanart.tv / TMDB / Shikimori görselleri) bu çapraz
 *     kimlikleri [resolveCrossIds] ile kullanır.
 *
 * Her fonksiyon hata durumunda boş sonuç döndürür; ağ hatası sayfayı düşürmez.
 */
object KitsugiBangumiDetailClient {
    private const val TAG = "KitsugiBangumiDetail"
    const val SOURCE = BangumiIdNamespace.SOURCE

    /**
     * Çapraz kimlik çözümü (AniList arama + ARM) için süre tavanı. ViewModel zenginleştirme adımını
     * 20 sn ile sınırlar (ardından TMDB TR meta + sıradaki bölüm sorgusu gelir); bu yüzden iki
     * tavanın toplamı (7 + 7 sn) bilinçli olarak bunun altında tutulur. Süre dolarsa o ana kadar
     * çözülenle devam edilir; çözülen kimlikler önbellekte kalır ve sekmeler/galeri bunları kullanır.
     */
    private const val CROSS_TIMEOUT_MS = 7_000L

    /** MAL/AniList/TMDB eşlik detayı için süre tavanı. */
    private const val COMPANION_TIMEOUT_MS = 7_000L

    /** Bangumi sayfasında ek kaynaklar (Shikimori · Kitsu · TMDB) için bütçe. */
    private const val EXTRA_TAXONOMY_TIMEOUT_MS = 4_500L

    /** İlişki/öneri başlık zenginleştirmesi için kayıt ve toplam süre tavanları. */
    private const val RELATION_TITLE_TIMEOUT_MS = 8_000L
    private const val RELATION_TITLE_BATCH_TIMEOUT_MS = 9_000L

    /** İlişki listeleri 100'e kadar çıkar; başlık varyantlarını listenin büyük bölümünde doldur. */
    private const val RELATION_TITLE_ENRICH_LIMIT = 64

    /** Liste uçlarında varyantı olmayan kişi/karakterler için doğrudan Bangumi infobox yedeği. */
    private const val ENTITY_NAME_LOOKUP_LIMIT = 60
    private const val ENTITY_NAME_LOOKUP_TIMEOUT_MS = 3_500L
    private const val ENTITY_NAME_BATCH_TIMEOUT_MS = 6_500L
    private const val ENTITY_NAME_CACHE_TTL_MS = 6 * 60 * 60 * 1000L
    private const val ENTITY_NAME_NEGATIVE_CACHE_TTL_MS = 10 * 60 * 60 * 1000L
    private val enrichmentRequestSemaphore = Semaphore(8)

    /**
     * Stüdyo/şirket adı → Bangumi kurum条目 kimliği (`/v0/search/persons`, `type = 2` 公司).
     * Detay sayfasındaki çiplerin tıklanabilmesi için gerekir; bulunamayan adlar `0` ile
     * işaretlenir ve tekrar tekrar aranmaz.
     */
    private const val STUDIO_LOOKUP_LIMIT = 24
    private const val STUDIO_LOOKUP_BATCH_TIMEOUT_MS = 10_000L

    /** Kurum adı araması: aynı ada sahip çok kayıt olabildiğinden ilk 5 yetersiz kalıyordu. */
    private const val STUDIO_SEARCH_LIMIT = 10

    /**
     * Bir kurum adı için en fazla kaç farklı yazımla arama yapılır
     * (`SHUEISHA` → sözlük köprüsü → `集英社` gibi). Her yazım bir ağ isteğidir.
     */
    private const val MAX_ORG_QUERY_VARIANTS = 4

    /**
     * Gevşek kurum adı eşleşmesi için en kısa normalize anahtar uzunluğu. Kısa kısaltmalar
     * (`TBS`, `NBC`) yanlış kurumlara bağlanmasın diye yalnızca yeterince uzun adlarda
     * "içerir" karşılaştırması yapılır.
     */
    private const val MIN_FUZZY_ORG_KEY = 6

    /** Bulunamayan kurumlar bu süre boyunca yeniden aranmaz (geçici hatalar bunun dışında). */
    private const val STUDIO_MISS_TTL_MS = 10 * 60 * 1000L
    private val studioLookupSemaphore = Semaphore(4)
    private val studioPersonIdCache = BoundedCache<String, Int>("bangumi.studioPersonId", 256)
    private val studioPersonImageCache = BoundedCache<String, String>("bangumi.studioPersonImage", 256)
    /** Kurum adı → "bu süreye kadar yeniden arama" zaman damgası (yalnızca başarılı-boş sonuçlar). */
    private val studioPersonMissUntil = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private data class EntityNameCacheEntry(
        val name: BangumiLocalizedName?,
        val cachedAtMs: Long
    )

    private val entityNameCache = BoundedCache<String, EntityNameCacheEntry>("bangumi.entityName", 1000)

    /** Bulunamayan eşleşmeler için olumsuz önbellek süresi (tekrar tekrar arama yapılmasın). */
    private const val CROSS_MISS_TTL_MS = 20 * 60 * 1000L

    private const val ANILIST_ACCEPT_SCORE = 5

    private const val XREF_PREFS = "bangumi_xref_cache_v1"

    // ── Önbellekler ──────────────────────────────────────────────────────────

    private val subjectCache = BoundedCache<Int, BangumiSubject>("bangumi.subject", 80)
    /** Definitive 404s only; timeouts/5xx/cancellation are never cached as "missing". */
    private val subjectMissCache = BoundedCache<String, Long>("bangumi.subject.miss", 256)
    private val crossCache = BoundedCache<Int, CrossIds>("bangumi.cross", 1000)
    private val crossMissAt = BoundedCache<Int, Long>("bangumi.crossMiss", 1000)

    /**
     * Çözümler çağıranın değil BU kapsamın içinde çalışır: detay (7 sn tavan), galeri (15 sn tavan) ve
     * sekmeler aynı anda istek yaptığında tek bir çözüm yürür; çağıranlardan biri süre aşımıyla
     * iptal olsa bile iş yarım kalıp baştan başlamaz, tamamlanınca önbelleğe yazılır.
     */
    private val resolveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlight = ConcurrentHashMap<Int, Deferred<CrossIds>>()

    /** Bangumi kaydının diğer platformlardaki karşılıkları. */
    data class CrossIds(
        val aniListId: Int? = null,
        val malId: Int? = null,
        val tmdbId: Int? = null,
        val kitsuId: Int? = null,
        val tvdbId: Int? = null,
        val imdbId: String? = null
    ) {
        val hasAnyId: Boolean
            get() = aniListId != null || malId != null || tmdbId != null || kitsuId != null

        /** AniList "stableId" biçimi (`100_000_000 + aniListId`). */
        val aniListStableId: Int?
            get() = aniListId?.takeIf { it > 0 }?.let { 100_000_000 + it }
    }

    // ── Kimlik yardımcıları ──────────────────────────────────────────────────

    /** stableId (500M+) ya da ham subject_id → ham subject_id. Geçersizse null. */
    fun rawIdOf(id: Int?): Int? {
        if (id == null || id <= 0) return null
        return BangumiIdNamespace.rawIdFromStable(id) ?: id.takeIf { it < BangumiIdNamespace.STABLE_ID_MIN }
    }

    private fun stableIdOf(rawId: Int?): Int? = BangumiIdNamespace.stableIdFromRaw(rawId)

    /** Çağıran taraflardan gelen `realMalId` değeri gerçek bir MAL ID'si değilse (stableId vb.) atılır. */
    fun sanitizeMalId(value: Int?): Int? = value?.takeIf { it in 1..99_999_999 }

    // ── Ağ yardımcıları ──────────────────────────────────────────────────────

    private suspend fun tokenOrNull(): String? {
        val context = KitsugiApplication.getInstance()?.applicationContext ?: return null
        return runCatching {
            if (BangumiAuthStore.isConnected(context)) BangumiAuthStore.getValidToken(context) else null
        }.getOrNull()
    }

    /**
     * `next.bgm.tv/p1` GET. Önce anonim denenir (herkese açık uçlar); sonuç yoksa ve oturum varsa
     * token ile bir kez daha denenir (NSFW条目 yalnızca yetkili hesaplara görünür).
     */
    private suspend fun p1(path: String, query: Map<String, String?> = emptyMap()): JSONObject? {
        suspend fun call(token: String?): JSONObject? = try {
            BangumiApiClient.getRaw(path, query, token = token, next = true)?.let { JSONObject(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "p1 $path başarısız: ${e.message}")
            null
        }
        call(null)?.let { return it }
        val token = tokenOrNull() ?: return null
        return call(token)
    }

    /** `GET /v0/subjects/{id}` (önbellekli). */
    suspend fun loadSubject(rawId: Int): BangumiSubject? {
        subjectCache[rawId]?.let { return it }
        val token = tokenOrNull()
        val missKey = BangumiSubjectLookupPolicy.missKey(rawId, token)
        val now = System.currentTimeMillis()
        if (BangumiSubjectLookupPolicy.isFreshMiss(subjectMissCache[missKey], now)) return null
        var definitiveNotFound = false
        suspend fun fetch(fetchToken: String?): BangumiSubject? = try {
            BangumiApiClient.getSubject(rawId, fetchToken)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e is BangumiApiClient.BangumiApiException && e.isNotFound) definitiveNotFound = true
            Log.w(TAG, "Bangumi条目 $rawId alınamadı (${if (fetchToken == null) "anonim" else "oturumlu"}): ${e.message}")
            null
        }
        val subject = fetch(null) ?: tokenOrNull()?.let { fetch(it) }
        if (subject != null) {
            if (subjectCache.size > 64) subjectCache.clear()
            subjectCache[rawId] = subject
            return subject
        }
        if (definitiveNotFound) {
            subjectMissCache[missKey] = now
        }
        return null
    }

    // ── JSON yardımcıları ────────────────────────────────────────────────────

    /** Android `optString` JSON `null` için "null" döndürür; burada boş metne çevrilir. */
    private fun JSONObject.str(key: String): String = if (isNull(key)) "" else optString(key, "").trim()

    private fun JSONObject.strOrNull(key: String): String? = str(key).takeIf { it.isNotEmpty() }

    private fun JSONObject?.image(vararg keys: String): String? {
        if (this == null) return null
        for (key in keys) {
            val value = strOrNull(key) ?: continue
            return BangumiApiClient.absoluteImageUrl(value)
        }
        return null
    }

    private fun JSONArray?.objects(): List<JSONObject> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optJSONObject(it) }
    }

    /**
     * p1 kısa nesnelerindeki adı dil alanlarını kaybetmeden çözer. `name` özgün ad,
     * `nameCN` ise Çince yerelleştirmedir; Çince ad artık tüm ekranlara zorla ana
     * başlık olarak yazılmaz.
     */
    private fun JSONObject.localizedName(
        aliases: List<String> = emptyList(),
        englishAliases: List<String> = emptyList(),
        romajiAliases: List<String> = emptyList()
    ): BangumiLocalizedName = BangumiNameLocalizer.entity(
        name = strOrNull("name"),
        nameCn = strOrNull("nameCN") ?: strOrNull("name_cn"),
        aliases = aliases,
        englishAliases = englishAliases,
        romajiAliases = romajiAliases
    )

    // ═════════════════════════════════════════════════════════════════════════
    // 1) ANA DETAY (Bangumi-yerel) — sayfayı hemen açar
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Yalnızca Bangumi verisinden oluşan ana detay. Hızlıdır (tek istek, önbellekli); diğer
     * platformlarla birleştirme [enrich] ile ayrı ve zaman tavanlı yapılır.
     */
    suspend fun fetchDetail(stableOrRawId: Int, mediaType: MediaType): KitsugiMediaDetail? =
        withContext(Dispatchers.IO) {
            val rawId = rawIdOf(stableOrRawId) ?: return@withContext null
            val subject = loadSubject(rawId) ?: return@withContext null
            val detail = buildNativeDetail(subject, mediaType)
            // İnfobox'tan gelen serbest metin kurum adlarını Bangumi'nin kendi şirket
            // kaydındaki küresel ada çevir (sözlükte olmayanlar dahil). Süre tavanlı ve
            // ek katmandır: çözülemezse ad olduğu gibi kalır, sayfa açılışı beklemeye takılmaz.
            val resolved = BangumiOrgResolver.resolveDetailOrgNames(detail)
            rememberLatinTitle(rawId, resolved)
            resolved
        }

    /**
     * Bir kaydın Latin (romaji / İngilizce) adını kalıcı önbelleğe yazar. Keşfet şeritleri ve
     * ilişki/öneri listeleri Bangumi'den yalnızca özgün adı aldığı için, burada çözülen ad
     * sonraki listelemelerde ağ isteği olmadan kullanılır.
     */
    private suspend fun rememberLatinTitle(rawId: Int, detail: KitsugiMediaDetail) {
        BangumiTitleCache.put(
            rawId = rawId,
            romaji = detail.titleRomaji
                ?: detail.title.takeIf { detail.titleEnglish.isNullOrBlank() },
            english = detail.titleEnglish,
            native = detail.titleNative ?: detail.titleJapanese
        )
    }

    /**
     * Bangumi detayını MAL / AniList / TMDB verisiyle tamamlar. Başarısız olursa [base] döner.
     * Dönen detayda `realMalId` ve `tmdbId` doludur; böylece Fanart.tv / TMDB / Shikimori galerisi,
     * bölüm puanları, logo ve MDBList aynı kimliklerle çalışır.
     */
    suspend fun enrich(stableOrRawId: Int, mediaType: MediaType, base: KitsugiMediaDetail): KitsugiMediaDetail =
        withContext(Dispatchers.IO) {
            val rawId = rawIdOf(stableOrRawId) ?: return@withContext base
            val resolved: CrossIds? = try {
                withTimeoutOrNull(CROSS_TIMEOUT_MS) { resolveCrossIds(rawId, mediaType) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            val cross = resolved ?: CrossIds()
            if (!cross.hasAnyId) {
                // Eşleşme yoksa bile çözülen Latin ad kalıcı olur: listeler sonraki ziyarette
                // İngilizce/romaji başlığı ağ isteği olmadan gösterir.
                rememberLatinTitle(rawId, base)
                return@withContext withStudioPersonIds(base)
            }
            val companion: KitsugiMediaDetail? = try {
                withTimeoutOrNull(COMPANION_TIMEOUT_MS) { fetchCompanionDetail(cross, mediaType) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Eşlik detayı alınamadı: ${e.message}")
                null
            }
            var merged = mergeDetail(base, companion, cross, mediaType)
            if (companion == null && (cross.aniListId != null || cross.malId != null)) {
                val alInfo = runCatching {
                    KitsugiAniListDetailClient.fetchTagsAndAiringInfo(
                        aniListId = cross.aniListId,
                        malId = cross.malId,
                        mediaType = mediaType
                    )
                }.getOrNull()
                if (alInfo != null && (alInfo.tags.isNotEmpty() || alInfo.nextAiringEpisode != null)) {
                    merged = merged.copy(
                        tags = KitsugiSpoilerRules.mergeTagsPreservingSpoilers(merged.tags, alInfo.tags, limit = 600),
                        nextAiringEpisode = merged.nextAiringEpisode ?: alInfo.nextAiringEpisode
                    )
                }
            }
            // Kalan kaynaklar (Shikimori · Kitsu · TMDB) da aynı havuza katılır: böylece
            // Bangumi sayfası, MAL/AniList sayfasıyla **aynı** tür/etiket/kurum kümesini
            // gösterir. Bangumi'nin kendi verisi zaten elde olduğu için yeniden aranmaz.
            // Ek toplama, 20 sn'lik zenginleştirme bütçesini aşmasın diye ayrıca tavanlanır:
            // Bangumi'nin kendi verisi + MAL/AniList birleşimi zaten yukarıda tamamlandı.
            val extra = runCatching {
                withTimeoutOrNull(EXTRA_TAXONOMY_TIMEOUT_MS) {
                    KitsugiTaxonomySources.collect(
                    malId = cross.malId,
                    aniListId = cross.aniListId,
                    mediaType = mediaType,
                    tmdbId = merged.tmdbId ?: cross.tmdbId,
                    titles = listOf(
                        merged.title,
                        merged.titleEnglish,
                        merged.titleRomaji,
                        merged.titleNative,
                        base.title
                    ) + merged.synonyms,
                    year = merged.year,
                    includeBangumi = false
                    )
                }
            }.getOrNull()
            if (extra != null && !extra.isEmpty) {
                merged = KitsugiTaxonomyMerge.mergeDetail(merged, extra.toDetail())
                Log.d(
                    TAG,
                    "Bangumi + tüm kaynaklar: tür=${merged.genres.size} etiket=${merged.tags.size} " +
                        "stüdyo=${merged.studios.size} yapımcı=${merged.producers.size} " +
                        "kanal=${merged.networks.size} dergi=${merged.serializations.size}"
                )
            }
            rememberLatinTitle(rawId, merged)
            withStudioPersonIds(merged)
        }

    private suspend fun fetchCompanionDetail(cross: CrossIds, mediaType: MediaType): KitsugiMediaDetail? =
        when (mediaType) {
            MediaType.Anime, MediaType.Manga -> {
                val fromMal = cross.malId?.let { malId ->
                    try {
                        KitsugiMalDetailClient.fetchDetail(malId, mediaType)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }
                }
                if (fromMal != null) {
                    // MAL/Jikan detayında AniList'in yapıma özel spoiler etiketleri (`isMediaSpoiler` /
                    // `isGeneralSpoiler`) ve yüzdelik `rank` bilgisi bulunmaz; bunları AniList'ten tamamla.
                    val alInfo = try {
                        KitsugiAniListDetailClient.fetchTagsAndAiringInfo(
                            aniListId = cross.aniListId,
                            malId = cross.malId,
                            mediaType = mediaType
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }
                    if (alInfo != null && (alInfo.tags.isNotEmpty() || alInfo.nextAiringEpisode != null)) {
                        fromMal.copy(
                            tags = KitsugiSpoilerRules.mergeTagsPreservingSpoilers(fromMal.tags, alInfo.tags, limit = 600),
                            nextAiringEpisode = fromMal.nextAiringEpisode ?: alInfo.nextAiringEpisode
                        )
                    } else {
                        fromMal
                    }
                } else {
                    cross.aniListStableId?.let { stable ->
                        try {
                            KitsugiAniListDetailClient.fetchDetail(stable, mediaType)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
            }
            MediaType.TvShow, MediaType.Movie -> cross.tmdbId?.let { tmdbId ->
                try {
                    TmdbApiClient().fetchMediaDetail(tmdbId, mediaType == MediaType.Movie)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
            }
        }

    internal fun buildNativeDetail(subject: BangumiSubject, mediaType: MediaType): KitsugiMediaDetail {
        val infobox = subject.infobox

        fun values(vararg keys: String): List<String> {
            for (key in keys) infobox[key]?.takeIf { it.isNotEmpty() }?.let { return it }
            return emptyList()
        }

        fun first(vararg keys: String): String? = values(*keys).firstOrNull()?.takeIf { it.isNotBlank() }

        /** Verilen anahtarların HEPSİNDEKİ değerleri birleştirir (örn. ana + diğer kanallar). */
        fun valuesAll(vararg keys: String): List<String> = keys.flatMap { infobox[it].orEmpty() }

        val localizedTitle = BangumiNameLocalizer.subject(subject.name, subject.nameCn, infobox)
        val primaryTitle = localizedTitle.display
        val synonyms = localizedTitle.alternatives

        val startIso = isoDate(first("放送开始", "放送開始", "上映年度", "发售日", "發售日", "开始", "首播") ?: subject.date)
            ?: isoDate(subject.date)
        val endIso = isoDate(first("播放结束", "放送结束", "播放結束", "结束", "完结"))
        val year = startIso?.take(4)?.toIntOrNull() ?: subject.date?.take(4)?.toIntOrNull()
        val isMovie = mediaType == MediaType.Movie || subject.platform?.contains("剧场版") == true
        val total = subject.eps.takeIf { it > 0 } ?: subject.totalEpisodes.takeIf { it > 0 }
            ?: first("话数", "話数")?.let { Regex("\\d+").find(it)?.value?.toIntOrNull() }?.takeIf { it > 0 }

        val rating = subject.rating
        val ratingScore = rating?.score?.takeIf { it > 0.0 }
        val collection = subject.collection
        val trackedTotal = collection?.total?.takeIf { it > 0 }

        // ── Stüdyo / yapımcı / kanal ─────────────────────────────────────────
        // Stüdyo/şirket adları infobox'ta Japonca/Çince gelir — Latin ada çevrilir
        // (örn. 京都アニメーション → Kyoto Animation); bilinmeyenler olduğu gibi kalır.
        //
        // Aynı kurum infobox'ta birden çok yazımla geçebiliyor
        // (`SHUEISHA` + `集英社`, `ジェネオン・ユニバーサル・エンターテイメント(NBC…)` +
        // `Geneon Universal Entertainment`). Sözlük kimliği (`bangumi_producer_*`) aynıysa
        // tek çipte toplanır; yoksa normalize edilmiş ad kullanılır. Bu olmadan Liste
        // sayfasında aynı şirket iki kez, biri ham Japonca olarak görünüyordu.
        fun distinctCompanies(names: List<String>): List<String> {
            val seen = HashSet<String>(names.size * 2)
            return names.filter { name ->
                val identity = BangumiOrgDictionary.identityKey(name)
                    ?: normalizeCompanyKey(name)
                identity.isNotEmpty() && seen.add(identity)
            }
        }

        val studioNames = distinctCompanies(
            splitCompanies(
                values(
                    "动画制作", "動畫製作", "动画制作公司", "動畫製作公司", "アニメーション制作",
                    "アニメーション製作", "アニメ制作", "制作", "制作公司", "製作公司", "制作スタジオ", "开发", "開發"
                )
            ).map { it.toLatinStudioName() }
        )
        val studios = studioNames
            .map { KitsugiStudio(id = 0, name = it, isMain = true, source = SOURCE, role = StudioRole.STUDIO) }
        val producers = if (mediaType == MediaType.Manga) {
            distinctCompanies(
                splitCompanies(valuesAll("出版社", "出版", "发行", "發行"))
                    .map { it.toLatinStudioName() }
            )
                .map { KitsugiStudio(id = 0, name = it, isMain = true, source = SOURCE, role = StudioRole.PUBLISHER) }
        } else {
            // Kurum rollerini kaynaklar arasında kayıpsız birleştir.
            distinctCompanies(
                splitCompanies(
                    valuesAll(
                        "製作", "出品", "发行", "發行", "发行公司", "製作委员会", "制作委员会",
                        "製作委員會", "制作協力", "製作協力", "音楽制作", "音乐制作", "音樂製作",
                        "レーベル", "厂牌", "廠牌", "配給", "配给", "プロデュース"
                    )
                )
                    .map { it.toLatinStudioName() }
                    .filter { name -> studioNames.none { it.equals(name, ignoreCase = true) } }
            )
                .map { KitsugiStudio(id = 0, name = it, isMain = false, source = SOURCE, role = StudioRole.PRODUCER) }
        }
        val networks = distinctCompanies(
            splitNetworks(
                valuesAll("播放电视台", "播放電視台", "其他电视台", "其他電視台", "放送局", "テレビ局")
            ).map { it.toLatinStudioName() }
        )
            .map { KitsugiStudio(id = 0, name = it, isMain = false, source = SOURCE, role = StudioRole.NETWORK) }
        val serializations = distinctCompanies(
            splitCompanies(valuesAll(
                "连载杂志", "連載雜誌", "連載雑誌", "杂志", "雜誌", "雑誌", "掲載誌", "連載", "杂志社"
            )).map { it.toLatinStudioName() }
        )
            .map { KitsugiStudio(id = 0, name = it, isMain = true, source = SOURCE, role = StudioRole.MAGAZINE) }

        // ── Yayın bilgisi ────────────────────────────────────────────────────
        val weekday = first("放送星期")?.let { weekdayEnglish(it) }
        val station = first("播放电视台", "播放電視台")?.toLocalizedOrgName()
        val broadcast = weekday?.let { day -> if (station != null) "$day ($station)" else day }
            ?.toTurkishBroadcast()
        val episodeDuration = first("片长", "片長", "时长")?.let { durationText(it, isMovie) }

        // ── Dış bağlantılar ──────────────────────────────────────────────────
        val links = mutableListOf(KitsugiExternalLink(site = "Bangumi", url = subject.webUrl))
        first("官方网站", "官方網站", "官网")?.takeIf { it.startsWith("http", ignoreCase = true) }?.let {
            links += KitsugiExternalLink(site = "Resmi Site", url = it)
        }

        val cover = BangumiApiClient.absoluteImageUrl(subject.images?.large ?: subject.images?.poster)

        return KitsugiMediaDetail(
            synopsis = subject.summary.replace("\r\n", "\n").replace('\r', '\n').trim().takeIf { it.isNotEmpty() },
            genres = metaGenres(subject.metaTags),
            status = statusOf(subject, startIso, endIso, mediaType)?.toTurkishStatus(),
            season = seasonOf(startIso, mediaType)?.toTurkishSeason(),
            sourceMaterial = first("原作")?.takeIf { mediaType != MediaType.Manga },
            studios = if (mediaType == MediaType.Manga) emptyList() else studios,
            producers = producers,
            broadcast = broadcast,
            episodeDuration = episodeDuration,
            startDate = startIso,
            endDate = endIso,
            titleEnglish = localizedTitle.english,
            titleJapanese = localizedTitle.native,
            titleRomaji = localizedTitle.romaji,
            titleNative = localizedTitle.native,
            synonyms = synonyms,
            title = primaryTitle,
            imageUrl = cover,
            type = mediaType,
            score = ratingScore?.let { Math.round(it).toInt().coerceIn(0, 10) },
            year = year,
            total = total,
            isAdult = subject.nsfw,
            // Etiketler arayüz diline çevrilir ve çeviriden sonra aynı anlama gelen yazımlar
            // (催泪 / 催涙 / Duygusal) tek çipe indirgenir. Modelde orijinal değer kalır; çip
            // aramayı orijinaliyle yapar. Doğal spoiler etiketler ilk andan itibaren işaretlenir.
            tags = subject.tags.localizedDistinctTags(400)
                .map {
                    KitsugiTag(
                        name = it,
                        rank = null,
                        isSpoiler = KitsugiSpoilerRules.isIntrinsicSpoiler(it),
                        source = SOURCE
                    )
                },
            externalLinks = links,
            pictures = emptyList(),
            meanScore = ratingScore?.let { (it * 10).toInt() },
            averageScore = ratingScore?.let { (it * 10).toInt() },
            popularity = trackedTotal,
            rank = rating?.rank?.takeIf { it > 0 },
            scoredBy = rating?.total?.takeIf { it > 0 },
            members = trackedTotal,
            networks = networks,
            serializations = serializations,
            format = when (mediaType) {
                MediaType.Movie -> "Movie"
                MediaType.TvShow -> "TV"
                else -> formatOf(subject.platform)
            },
            volumes = subject.volumes.takeIf { it > 0 },
            rawFormat = subject.platform,
            seasonYear = year
        )
    }

    internal fun mergeDetail(
        base: KitsugiMediaDetail,
        other: KitsugiMediaDetail?,
        cross: CrossIds,
        mediaType: MediaType
    ): KitsugiMediaDetail {
        val links = (base.externalLinks + crossLinks(cross, mediaType) + other?.externalLinks.orEmpty())
            .filter { it.url.isNotBlank() }
            .distinctBy { it.url.trim().trimEnd('/').lowercase(Locale.ROOT) }
        if (other == null) {
            return base.copy(
                realMalId = cross.malId ?: base.realMalId,
                tmdbId = cross.tmdbId ?: base.tmdbId,
                tags = KitsugiSpoilerRules.applySpoilerFlags(base.tags),
                externalLinks = links
            )
        }
        // Etiketler birleştirilirken modelde HER ZAMAN kaynağın özgün adı kalır (çipe
        // tıklandığında arama o değerle yapılır); çeviri gösterim katmanındadır. Tekilleştirme
        // ise çevrilmiş hâline ve kanonik anahtara göre yapılır; eşleşen etiketlerde AniList'in
        // `isSpoiler` ve `rank` bilgileri Bangumi etiketine aktarılır.
        val mergedTags = KitsugiSpoilerRules.mergeTagsPreservingSpoilers(
            primary = base.tags,
            secondary = other.tags,
            limit = 600
        )
        // ── Tür / kurum birleşimi (yerine koyma DEĞİL) ──────────────────────────
        // Bangumi'nin kendi türleri ve infobox kurumları (动画制作 / 製作 / 播放电视台 /
        // 出版社 / 连载杂志) korunur; MAL/AniList/TMDB'nin eklediği türler ve kurumlar
        // ÜZERİNE eklenir. Böylece iki kaynakta da olan bir stüdyo tek çipte kalır.
        // Kurum adları modele yazılmadan önce temizlenir: infobox artıkları (tırnak,
        // `; B6版` notları, kaomoji) gösterimde stüdyo/yayıncı olarak görünmez.
        val mergedGenres = KitsugiTaxonomyMerge.mergeGenres(base.genres, other.genres)
        val mergedStudios = KitsugiTaxonomyMerge.normalizeOrgNames(
            KitsugiTaxonomyMerge.mergeOrgs(base.studios, other.studios)
        )
        // Rol duyarlı birleştirme: Bangumi infobox'ında hem 动画制作 hem 製作 altında
        // geçen şirket iki rolde de korunur (eskiden yapımcı kaydı siliniyordu).
        val mergedProducers = KitsugiTaxonomyMerge.normalizeOrgNames(
            KitsugiTaxonomyMerge.mergeOrgs(base.producers, other.producers)
        )
        val mergedNetworks = KitsugiTaxonomyMerge.normalizeOrgNames(
            KitsugiTaxonomyMerge.mergeOrgs(base.networks, other.networks)
        )
        val mergedSerializations = KitsugiTaxonomyMerge.normalizeOrgNames(
            KitsugiTaxonomyMerge.mergeOrgs(base.serializations, other.serializations)
        )
        return base.copy(
            // Özet tercihi: Bangumi açıklaması Çince/Japonca geldiyse ve eşleşen kayıtta Latin
            // (İngilizce) açıklama varsa O kullanılır — otomatik çeviri İngilizce kaynaktan
            // belirgin şekilde daha iyi sonuç verir. Bangumi özgün dili Latinse Bangumi korunur.
            synopsis = pickSynopsis(base.synopsis, other.synopsis),
            // Türler: Bangumi meta etiketleri + diğer platformun türleri BİRLEŞİR.
            genres = mergedGenres,
            status = base.status ?: other.status,
            season = base.season ?: other.season,
            sourceMaterial = other.sourceMaterial ?: base.sourceMaterial,
            studios = mergedStudios,
            producers = mergedProducers,
            rating = other.rating ?: base.rating,
            broadcast = base.broadcast ?: other.broadcast,
            episodeDuration = base.episodeDuration ?: other.episodeDuration,
            startDate = base.startDate ?: other.startDate,
            endDate = base.endDate ?: other.endDate,
            titleEnglish = other.titleEnglish ?: base.titleEnglish,
            titleRomaji = other.titleRomaji ?: base.titleRomaji,
            synonyms = (base.synonyms + other.synonyms).filter { it.isNotBlank() }.distinct(),
            openings = other.openings,
            endings = other.endings,
            trailerUrl = other.trailerUrl ?: base.trailerUrl,
            bannerImage = other.bannerImage ?: base.bannerImage,
            isAdult = base.isAdult || other.isAdult,
            realMalId = cross.malId ?: other.realMalId ?: base.realMalId,
            tags = mergedTags,
            externalLinks = links,
            streamingLinks = other.streamingLinks.ifEmpty { base.streamingLinks },
            streamingEpisodes = other.streamingEpisodes.ifEmpty { base.streamingEpisodes },
            tmdbId = cross.tmdbId ?: other.tmdbId ?: base.tmdbId,
            tmdbSeason = other.tmdbSeason ?: base.tmdbSeason,
            // Galeri: MAL / AniList / TMDB kapakları ve afişleri de eklenir (Bangumi kapağı ana görseldir).
            pictures = (base.pictures + other.pictures + listOfNotNull(other.imageUrl, other.bannerImage))
                .filter { it.isNotBlank() && galleryDedupKey(it) != base.imageUrl?.let { cover -> galleryDedupKey(cover) } }
                .distinctBy { galleryDedupKey(it) },
            totalSeasons = other.totalSeasons ?: base.totalSeasons,
            nextAiringEpisode = other.nextAiringEpisode ?: base.nextAiringEpisode,
            favorites = other.favorites ?: base.favorites,
            popularityRank = other.popularityRank ?: base.popularityRank,
            themes = KitsugiTaxonomyMerge.mergeGenres(base.themes, other.themes, limit = 64),
            demographics = KitsugiTaxonomyMerge.mergeGenres(base.demographics, other.demographics, limit = 32),
            serializations = mergedSerializations,
            networks = mergedNetworks,
            authors = other.authors.ifEmpty { base.authors },
            rankings = other.rankings.ifEmpty { base.rankings },
            countryOfOrigin = other.countryOfOrigin ?: base.countryOfOrigin,
            originalLanguage = other.originalLanguage ?: base.originalLanguage,
            tagline = other.tagline ?: base.tagline,
            budget = other.budget ?: base.budget,
            revenue = other.revenue ?: base.revenue,
            volumes = base.volumes ?: other.volumes
        )
    }

    private fun crossLinks(cross: CrossIds, mediaType: MediaType): List<KitsugiExternalLink> = buildList {
        val kind = if (mediaType == MediaType.Manga) "manga" else "anime"
        if (mediaType == MediaType.Anime || mediaType == MediaType.Manga) {
            cross.malId?.let { add(KitsugiExternalLink("MyAnimeList", "https://myanimelist.net/$kind/$it")) }
            cross.aniListId?.let { add(KitsugiExternalLink("AniList", "https://anilist.co/$kind/$it")) }
            cross.kitsuId?.let { add(KitsugiExternalLink("Kitsu", "https://kitsu.app/$kind/$it")) }
        } else {
            cross.tmdbId?.let {
                val path = if (mediaType == MediaType.Movie) "movie" else "tv"
                add(KitsugiExternalLink("TMDB", "https://www.themoviedb.org/$path/$it"))
            }
        }
        cross.imdbId?.takeIf { it.isNotBlank() }?.let { add(KitsugiExternalLink("IMDb", "https://www.imdb.com/title/$it/")) }
        cross.tvdbId?.let { add(KitsugiExternalLink("TheTVDB", "https://thetvdb.com/?tab=series&id=$it")) }
    }

    /**
     * Galeri tekilleştirme anahtarı. `lain.bgm.tv` aynı kapağı farklı boyut yollarıyla sunar
     * (`/pic/cover/l/..`, `/r/400/pic/cover/l/..`, `/r/800/..`, `/r/100/..`); bunlar aynı görseldir ve
     * galeride kopya görünmemelidir. Diğer URL'ler olduğu gibi anahtar olur.
     */
    fun galleryDedupKey(url: String): String {
        val match = BANGUMI_IMAGE_REGEX.find(url)
        return if (match != null) "bgm:${match.groupValues[1]}" else url
    }

    private val BANGUMI_IMAGE_REGEX = Regex("lain\\.bgm\\.tv/(?:r/\\d+/)?pic/[a-z]+/[a-z]/(.+)$")

    // ═════════════════════════════════════════════════════════════════════════
    // 2) ÇAPRAZ KİMLİK ÇÖZÜMÜ (Bangumi → AniList / MAL / TMDB / Kitsu ...)
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Bangumi'de MAL/AniList kimliği veren bir uç yoktur (ARM de Bangumi anahtarını içermez).
     * Bu yüzden:
     *  1. Kalıcı/bellek önbelleği,
     *  2. AniList araması (Bangumi özgün adı + takma adlar) — başlık + yıl + bölüm sayısı + format
     *     puanlamasıyla SIKI eşleştirme → AniList id + idMal,
     *  3. AniList bulamazsa Jikan (MAL) araması,
     *  4. ARM ile TMDB / Kitsu / TVDB / IMDb kimlikleri.
     * Canlı çekim (三次元) kayıtlarda TMDB araması kullanılır. Yanlış eşleşme yerine "eşleşme yok"
     * tercih edilir; olumsuz sonuç 20 dk bellekte tutulur.
     */
    suspend fun resolveCrossIds(stableOrRawId: Int?, mediaType: MediaType, subjectHint: BangumiSubject? = null): CrossIds {
        val rawId = rawIdOf(stableOrRawId) ?: return CrossIds()
        crossCache[rawId]?.let { return it }
        loadPersistedCross(rawId)?.let {
            crossCache[rawId] = it
            return it
        }
        val missAt = crossMissAt[rawId]
        if (missAt != null && System.currentTimeMillis() - missAt < CROSS_MISS_TTL_MS) return CrossIds()

        val job = inFlight.computeIfAbsent(rawId) {
            resolveScope.async {
                try {
                    val resolved = try {
                        doResolveCross(rawId, mediaType, subjectHint)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Çapraz kimlik çözümü başarısız ($rawId): ${e.message}")
                        CrossIds()
                    }
                    if (resolved.hasAnyId) {
                        crossCache[rawId] = resolved
                        persistCross(rawId, resolved, mediaType)
                    } else {
                        crossMissAt[rawId] = System.currentTimeMillis()
                    }
                    resolved
                } finally {
                    inFlight.remove(rawId)
                }
            }
        }
        return job.await()
    }

    private suspend fun doResolveCross(rawId: Int, mediaType: MediaType, hint: BangumiSubject?): CrossIds {
        val subject = hint ?: loadSubject(rawId) ?: return CrossIds()
        return when (mediaType) {
            MediaType.Anime, MediaType.Manga -> {
                val anilistType = if (mediaType == MediaType.Manga) "MANGA" else "ANIME"
                val match = matchOnAniList(subject, anilistType) ?: matchOnJikan(subject, mediaType)
                if (match == null) {
                    Log.i(TAG, "Bangumi $rawId için AniList/MAL eşleşmesi bulunamadı")
                    return CrossIds()
                }
                val resolved = runCatching {
                    KitsugiIdResolver.resolveIds(
                        malId = match.malId,
                        aniListId = match.aniListId,
                        tmdbId = null,
                        mediaType = mediaType,
                        kitsuId = null
                    )
                }.getOrNull()
                CrossIds(
                    aniListId = resolved?.aniListId ?: match.aniListId,
                    malId = resolved?.malId ?: match.malId,
                    tmdbId = resolved?.tmdbId,
                    kitsuId = resolved?.kitsuId,
                    tvdbId = resolved?.tvdbId,
                    imdbId = resolved?.imdbId
                )
            }
            MediaType.TvShow, MediaType.Movie -> matchOnTmdb(subject, mediaType)
        }
    }

    private data class Match(val aniListId: Int?, val malId: Int?, val score: Int)

    private fun searchQueries(subject: BangumiSubject): List<String> {
        // Bangumi infobox may contain several English/alternate names (sometimes under localized
        // keys). Previously only the first Latin alias was queried, so TMDB often never saw the
        // title it actually indexes (e.g. a localized name preceding the English title).
        val aliases = subject.infobox.values.flatten()
        val preferred = aliases.filter { isMostlyLatin(it) && it.any(Char::isLetter) }
        return (preferred + listOf(subject.name, subject.nameCn) + aliases)
            .map(::sanitizeQuery)
            .filter { it.length >= 2 }
            .distinct()
            .take(8)
    }

    internal fun sanitizeQuery(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFKC)
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()

    internal fun normalizeTitle(text: String?): String =
        Normalizer.normalize(text.orEmpty(), Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .filter { it.isLetterOrDigit() }

    private fun subjectTitleSet(subject: BangumiSubject): Set<String> =
        (listOf(subject.name, subject.nameCn) + subject.infobox.values.flatten())
            .map { normalizeTitle(it) }
            .filter { it.length >= 2 }
            .toSet()

    /** Başlık/yıl/bölüm/format puanlaması — eşik [ANILIST_ACCEPT_SCORE]. Yıl farkı ≥ 2 ise elenir. */
    internal fun scoreCandidate(
        subjectTitles: Set<String>,
        subjectYear: Int?,
        subjectEpisodes: Int,
        candidateTitles: List<String?>,
        candidateYear: Int?,
        candidateEpisodes: Int?,
        formatCompatible: Boolean?
    ): Int {
        val cand = candidateTitles.map { normalizeTitle(it) }.filter { it.length >= 2 }.toSet()
        var score = 0
        score += when {
            subjectTitles.any { it in cand } -> 4
            subjectTitles.any { s -> cand.any { c -> s.length >= 5 && c.length >= 5 && (s.contains(c) || c.contains(s)) } } -> 1
            else -> return 0
        }
        if (subjectYear != null && candidateYear != null) {
            val diff = kotlin.math.abs(subjectYear - candidateYear)
            score += when {
                diff == 0 -> 2
                diff == 1 -> 1
                else -> return 0
            }
        }
        if (subjectEpisodes > 0 && candidateEpisodes != null && candidateEpisodes > 0 && subjectEpisodes == candidateEpisodes) score += 1
        if (formatCompatible == true) score += 1 else if (formatCompatible == false) score -= 2
        return score
    }

    internal fun formatCompatible(platform: String?, anilistFormat: String?): Boolean? {
        val format = anilistFormat?.uppercase(Locale.ROOT) ?: return null
        val p = platform?.trim().orEmpty()
        return when {
            p.isEmpty() -> null
            p.equals("TV", ignoreCase = true) -> format in setOf("TV", "TV_SHORT", "ONA")
            p.equals("OVA", ignoreCase = true) -> format in setOf("OVA", "SPECIAL", "ONA")
            p.contains("剧场版") -> format == "MOVIE"
            p.equals("WEB", ignoreCase = true) -> format in setOf("ONA", "TV", "OVA", "TV_SHORT")
            p.contains("漫画") -> format in setOf("MANGA", "ONE_SHOT")
            p.contains("小说") -> format == "NOVEL"
            else -> null
        }
    }

    private suspend fun matchOnAniList(subject: BangumiSubject, anilistType: String): Match? {
        val titles = subjectTitleSet(subject)
        if (titles.isEmpty()) return null
        val subjectYear = subject.date?.take(4)?.toIntOrNull()
        val query = """
            query (${'$'}search: String, ${'$'}type: MediaType) {
              Page(page: 1, perPage: 8) {
                media(search: ${'$'}search, type: ${'$'}type, sort: SEARCH_MATCH) {
                  id idMal format episodes chapters
                  startDate { year }
                  title { romaji english native }
                  synonyms
                }
              }
            }
        """.trimIndent()
        for (q in searchQueries(subject)) {
            val response = try {
                KitsugiApiBase.executeAniListQuery(query, JSONObject().put("search", q).put("type", anilistType))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            } ?: continue
            val media = runCatching {
                JSONObject(response).optJSONObject("data")?.optJSONObject("Page")?.optJSONArray("media").objects()
            }.getOrDefault(emptyList())
            var best: Match? = null
            for (m in media) {
                val id = m.optInt("id", 0).takeIf { it > 0 } ?: continue
                val title = m.optJSONObject("title")
                val synonyms = m.optJSONArray("synonyms")?.let { arr -> (0 until arr.length()).map { arr.optString(it) } }.orEmpty()
                val episodes = if (anilistType == "MANGA") m.optInt("chapters", 0) else m.optInt("episodes", 0)
                val score = scoreCandidate(
                    subjectTitles = titles,
                    subjectYear = subjectYear,
                    subjectEpisodes = if (anilistType == "MANGA") 0 else subject.eps.takeIf { it > 0 } ?: subject.totalEpisodes,
                    candidateTitles = listOf(title?.strOrNull("romaji"), title?.strOrNull("english"), title?.strOrNull("native")) + synonyms,
                    candidateYear = m.optJSONObject("startDate")?.optInt("year", 0)?.takeIf { it > 0 },
                    candidateEpisodes = episodes.takeIf { it > 0 },
                    formatCompatible = formatCompatible(subject.platform, m.strOrNull("format"))
                )
                if (score >= ANILIST_ACCEPT_SCORE && (best == null || score > best.score)) {
                    best = Match(aniListId = id, malId = m.optInt("idMal", 0).takeIf { it > 0 }, score = score)
                }
            }
            if (best != null) {
                Log.i(TAG, "Bangumi ${subject.id} → AniList ${best.aniListId} / MAL ${best.malId} (puan=${best.score}, q='$q')")
                return best
            }
        }
        return null
    }

    private suspend fun matchOnJikan(subject: BangumiSubject, mediaType: MediaType): Match? {
        val titles = subjectTitleSet(subject)
        if (titles.isEmpty()) return null
        val subjectYear = subject.date?.take(4)?.toIntOrNull()
        val endpoint = if (mediaType == MediaType.Manga) "manga" else "anime"
        for (q in searchQueries(subject)) {
            val url = runCatching {
                URL("https://api.jikan.moe/v4/$endpoint?q=${URLEncoder.encode(q, "UTF-8")}&limit=8")
            }.getOrNull() ?: continue
            val response = try {
                KitsugiApiBase.executeGetRequestResilient(url)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            } ?: continue
            val items = runCatching { JSONObject(response).optJSONArray("data").objects() }.getOrDefault(emptyList())
            var best: Match? = null
            for (item in items) {
                val malId = item.optInt("mal_id", 0).takeIf { it > 0 } ?: continue
                val alt = item.optJSONArray("titles").objects().map { it.strOrNull("title") }
                val year = item.optInt("year", 0).takeIf { it > 0 }
                    ?: item.optJSONObject("aired")?.optJSONObject("prop")?.optJSONObject("from")?.optInt("year", 0)?.takeIf { it > 0 }
                    ?: item.optJSONObject("published")?.optJSONObject("prop")?.optJSONObject("from")?.optInt("year", 0)?.takeIf { it > 0 }
                val episodes = if (mediaType == MediaType.Manga) 0 else item.optInt("episodes", 0)
                val score = scoreCandidate(
                    subjectTitles = titles,
                    subjectYear = subjectYear,
                    subjectEpisodes = if (mediaType == MediaType.Manga) 0 else subject.eps.takeIf { it > 0 } ?: subject.totalEpisodes,
                    candidateTitles = listOf(item.strOrNull("title"), item.strOrNull("title_english"), item.strOrNull("title_japanese")) + alt,
                    candidateYear = year,
                    candidateEpisodes = episodes.takeIf { it > 0 },
                    formatCompatible = null
                )
                if (score >= ANILIST_ACCEPT_SCORE && (best == null || score > best.score)) {
                    best = Match(aniListId = null, malId = malId, score = score)
                }
            }
            if (best != null) {
                Log.i(TAG, "Bangumi ${subject.id} → MAL ${best.malId} (Jikan, puan=${best.score}, q='$q')")
                return best
            }
        }
        return null
    }

    private suspend fun matchOnTmdb(subject: BangumiSubject, mediaType: MediaType): CrossIds {
        val hit = findMatchingTmdbTitle(subject, mediaType) ?: return CrossIds()
        val tmdbId = hit.tmdbId?.takeIf { it > 0 } ?: return CrossIds()
        val resolved = runCatching {
            KitsugiIdResolver.resolveIds(malId = null, aniListId = null, tmdbId = tmdbId, mediaType = mediaType)
        }.getOrNull()
        return CrossIds(
            aniListId = resolved?.aniListId,
            malId = resolved?.malId,
            tmdbId = tmdbId,
            kitsuId = resolved?.kitsuId,
            tvdbId = resolved?.tvdbId,
            imdbId = resolved?.imdbId
        )
    }

    /**
     * Canlı-aksiyon Bangumi başlıklarını listede İngilizceyle göstermek ve detay eşleştirmesi
     * için aynı katı TMDB aramasını paylaşır. Başlık + yıl + medya türü doğrulanmadan eşleşme
     * kabul edilmez; yanlış kaydın İngilizce adı listeye taşınmaz.
     */
    internal suspend fun findMatchingTmdbTitle(
        rawId: Int,
        mediaType: MediaType,
        subjectHint: BangumiSubject? = null
    ): JikanSearchResult? {
        val subject = subjectHint ?: loadSubject(rawId) ?: return null
        return findMatchingTmdbTitle(subject, mediaType)
    }

    private suspend fun findMatchingTmdbTitle(
        subject: BangumiSubject,
        mediaType: MediaType
    ): JikanSearchResult? {
        val titles = subjectTitleSet(subject)
        val subjectYear = subject.date?.take(4)?.toIntOrNull()
        for (query in listOfNotNull(subject.nameCn.takeIf { it.isNotBlank() }, subject.name.takeIf { it.isNotBlank() }).distinct()) {
            val results = try {
                TmdbApiClient().search(query)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            val hit = results.firstOrNull { result ->
                val tmdbId = result.tmdbId ?: return@firstOrNull false
                if (tmdbId <= 0 || result.type != mediaType) return@firstOrNull false
                val names = listOf(result.title, result.titleEnglish, result.titleJapanese)
                    .map { normalizeTitle(it) }
                    .filter { it.length >= 2 }
                val titleMatch = names.any { it in titles }
                val yearMatch = subjectYear != null && result.year != null && kotlin.math.abs(subjectYear - result.year) <= 1
                titleMatch && (yearMatch || subjectYear == null || result.year == null)
            }
            if (hit != null) return hit
        }
        return null
    }

    // ── Kalıcı önbellek ──────────────────────────────────────────────────────

    private fun loadPersistedCross(rawId: Int): CrossIds? {
        val context = KitsugiApplication.getInstance()?.applicationContext ?: return null
        val encoded = runCatching {
            context.getSharedPreferences(XREF_PREFS, android.content.Context.MODE_PRIVATE).getString("s$rawId", null)
        }.getOrNull() ?: return null
        val map = encoded.split(';').mapNotNull {
            val index = it.indexOf('=')
            if (index <= 0) null else it.substring(0, index) to it.substring(index + 1)
        }.toMap()
        val ids = CrossIds(
            aniListId = map["al"]?.toIntOrNull(),
            malId = map["mal"]?.toIntOrNull(),
            tmdbId = map["tmdb"]?.toIntOrNull(),
            kitsuId = map["kitsu"]?.toIntOrNull(),
            tvdbId = map["tvdb"]?.toIntOrNull(),
            imdbId = map["imdb"]?.takeIf { it.isNotBlank() }
        )
        return ids.takeIf { it.hasAnyId }
    }

    private fun persistCross(rawId: Int, ids: CrossIds, mediaType: MediaType = MediaType.Anime) {
        val context = KitsugiApplication.getInstance()?.applicationContext ?: return
        val encoded = buildString {
            ids.aniListId?.let { append("al=").append(it).append(';') }
            ids.malId?.let { append("mal=").append(it).append(';') }
            ids.tmdbId?.let { append("tmdb=").append(it).append(';') }
            ids.kitsuId?.let { append("kitsu=").append(it).append(';') }
            ids.tvdbId?.let { append("tvdb=").append(it).append(';') }
            ids.imdbId?.let { append("imdb=").append(it).append(';') }
        }
        runCatching {
            context.getSharedPreferences(XREF_PREFS, android.content.Context.MODE_PRIVATE)
                .edit().putString("s$rawId", encoded).apply()
        }
        // Detay sayfasında doğrulanan MAL ↔ Bangumi köprüsünü `BangumiLocalMappingCache`
        // içine de yaz (`E99`) ki `Listem > Tümü` (`MyListIdentityCrosswalk`) aynı yapımı
        // anında tek kartta birleştirsin.
        val malId = ids.malId?.takeIf { it > 0 }
        if (malId != null && (mediaType == MediaType.Anime || mediaType == MediaType.Manga)) {
            runCatching {
                BangumiLocalMappingCache.put(
                    context,
                    malId,
                    BangumiIdNamespace.subjectTypeFor(mediaType),
                    rawId
                )
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 3) SEKMELER (Bangumi-yerel)
    // ═════════════════════════════════════════════════════════════════════════

    /** Karakterler + seslendirmenler (`/p1/subjects/{id}/characters`). */
    suspend fun fetchCharacters(stableOrRawId: Int, mediaType: MediaType): List<KitsugiCharacter> =
        withContext(Dispatchers.IO) {
            val rawId = rawIdOf(stableOrRawId) ?: return@withContext emptyList()
            val result = mutableListOf<KitsugiCharacter>()
            var offset = 0
            var total = Int.MAX_VALUE
            var page = 0
            while (offset < total && page < 3) {
                val root = p1("/subjects/$rawId/characters", mapOf("limit" to "60", "offset" to offset.toString())) ?: break
                total = root.optInt("total", 0)
                val parsed = parseCharacters(root)
                val pageSize = root.optJSONArray("data").objects().size
                if (pageSize == 0) break
                result += parsed
                offset += pageSize
                page++
            }
            result.distinctBy { it.id }
        }

    /** `/p1/subjects/{id}/characters` yanıtı → karakter listesi (ağdan bağımsız, test edilebilir). */
    internal fun parseCharacters(root: JSONObject): List<KitsugiCharacter> =
        root.optJSONArray("data").objects().mapNotNull { item ->
            val character = item.optJSONObject("character") ?: return@mapNotNull null
            val id = character.optInt("id", 0).takeIf { it > 0 } ?: return@mapNotNull null
            val actors = item.optJSONArray("casts").objects().mapNotNull { cast ->
                val person = cast.optJSONObject("person") ?: return@mapNotNull null
                val personId = person.optInt("id", 0).takeIf { it > 0 } ?: return@mapNotNull null
                val personName = person.localizedName()
                KitsugiVoiceActor(
                    id = personId,
                    name = personName.display,
                    language = castLanguage(cast.optInt("relation", 0)),
                    imageUrl = person.optJSONObject("images").image("medium", "large", "grid", "small"),
                    source = SOURCE,
                    romanizedName = personName.romaji,
                    nativeName = personName.native,
                    englishName = personName.english
                )
            }.distinctBy { it.id }
            val characterName = character.localizedName()
            KitsugiCharacter(
                id = id,
                name = characterName.display,
                role = characterRole(item.optInt("type", 2)).toTurkishCharacterRole(),
                imageUrl = character.optJSONObject("images").image("medium", "large", "grid", "small"),
                voiceActors = actors,
                source = SOURCE,
                romanizedName = characterName.romaji,
                nativeName = characterName.native,
                englishName = characterName.english
            )
        }

    /** Ekip (`/p1/subjects/{id}/staffs/persons`). */
    suspend fun fetchStaff(stableOrRawId: Int, mediaType: MediaType): List<KitsugiStaff> =
        withContext(Dispatchers.IO) {
            val rawId = rawIdOf(stableOrRawId) ?: return@withContext emptyList()
            val root = p1("/subjects/$rawId/staffs/persons", mapOf("limit" to "100")) ?: return@withContext emptyList()
            parseStaff(root)
        }

    /** `/p1/subjects/{id}/staffs/persons` yanıtı → ekip listesi. */
    internal fun parseStaff(root: JSONObject): List<KitsugiStaff> =
        root.optJSONArray("data").objects().mapNotNull { item ->
            val staff = item.optJSONObject("staff") ?: return@mapNotNull null
            val id = staff.optInt("id", 0).takeIf { it > 0 } ?: return@mapNotNull null
            val roles = item.optJSONArray("positions").objects()
                .mapNotNull { pos ->
                    val type = pos.optJSONObject("type")
                    type?.strOrNull("en") ?: type?.strOrNull("cn") ?: type?.strOrNull("jp")
                }
                .distinct()
                .take(3)
                .joinToString(", ")
                .ifBlank { "Staff" }
            val staffName = staff.localizedName()
            KitsugiStaff(
                id = id,
                name = staffName.display,
                role = roles.toTurkishStaffRole(),
                imageUrl = staff.optJSONObject("images").image("medium", "large", "grid", "small"),
                source = SOURCE,
                romanizedName = staffName.romaji,
                nativeName = staffName.native,
                englishName = staffName.english
            )
        }.distinctBy { it.id }

    private data class EntityNameLookup(val kind: String, val id: Int) {
        val cacheKey: String get() = "$kind:$id"
    }

    /** AniList kapsamı dışındaki ya da CJK adı kalmış Bangumi karakterlerini infobox'tan tamamlar. */
    suspend fun enrichCharacterNamesFromBangumi(
        characters: List<KitsugiCharacter>,
        background: Boolean = false,
        cacheOnly: Boolean = false
    ): List<KitsugiCharacter> {
        if (characters.isEmpty()) return characters

        // 1) AniList birebir eşleşmesi (kalıcı disk önbellekli). Karakter infobox'larında
        //    İngilizce/romaji adı çok seyrek bulunduğundan asıl çözüm buradadır.
        val nameRequests = buildList {
            characters
                .filter { needsLatinName(it.name, it.romanizedName, it.englishName) }
                .forEach {
                    add(
                        BangumiEntityNameResolver.Request(
                            BangumiEntityNameResolver.Kind.CHARACTER,
                            it.id,
                            it.nativeName,
                            it.name
                        )
                    )
                }
            characters.flatMap { it.voiceActors }
                .filter { needsLatinName(it.name, it.romanizedName, it.englishName) }
                .forEach {
                    add(
                        BangumiEntityNameResolver.Request(
                            BangumiEntityNameResolver.Kind.PERSON,
                            it.id,
                            it.nativeName,
                            it.name
                        )
                    )
                }
        }
        val resolved = BangumiEntityNameResolver.resolve(nameRequests, background, cacheOnly)
        val preEnriched = characters.map { character ->
            val variant = resolved[
                BangumiEntityNameResolver.cacheKey(BangumiEntityNameResolver.Kind.CHARACTER, character.id)
            ]
            val namedCharacter = if (variant != null) {
                character.copy(
                    name = latinDisplayName(character.name, variant),
                    romanizedName = usableLatinName(character.romanizedName) ?: variant.romaji,
                    nativeName = character.nativeName ?: variant.native,
                    englishName = usableLatinName(character.englishName) ?: variant.english
                )
            } else character
            namedCharacter.copy(
                voiceActors = namedCharacter.voiceActors.map { actor ->
                    val vv = resolved[
                        BangumiEntityNameResolver.cacheKey(BangumiEntityNameResolver.Kind.PERSON, actor.id)
                    ] ?: return@map actor
                    actor.copy(
                        name = latinDisplayName(actor.name, vv),
                        romanizedName = usableLatinName(actor.romanizedName) ?: vv.romaji,
                        nativeName = actor.nativeName ?: vv.native,
                        englishName = usableLatinName(actor.englishName) ?: vv.english
                    )
                }
            )
        }
        // 2) AniList'te olmayan kayıtlar için Bangumi infobox yedeği.
        val lookups = buildList {
            preEnriched.forEach { character ->
                if (needsLatinName(character.name, character.romanizedName, character.englishName)) {
                    add(EntityNameLookup("characters", character.id))
                }
                character.voiceActors.forEach { actor ->
                    if (needsLatinName(actor.name, actor.romanizedName, actor.englishName)) {
                        add(EntityNameLookup("persons", actor.id))
                    }
                }
            }
        }
        val names = fetchEntityLocalizedNames(lookups)
        return preEnriched.map { character ->
            val characterName = names[EntityNameLookup("characters", character.id).cacheKey]
            character.copy(
                name = localizedDisplay(character.name, characterName),
                romanizedName = usableLatinName(character.romanizedName) ?: characterName?.romaji,
                nativeName = character.nativeName ?: characterName?.native,
                englishName = usableLatinName(character.englishName) ?: characterName?.english,
                voiceActors = character.voiceActors.map { actor ->
                    val actorName = names[EntityNameLookup("persons", actor.id).cacheKey]
                    actor.copy(
                        name = localizedDisplay(actor.name, actorName),
                        romanizedName = usableLatinName(actor.romanizedName) ?: actorName?.romaji,
                        nativeName = actor.nativeName ?: actorName?.native,
                        englishName = usableLatinName(actor.englishName) ?: actorName?.english
                    )
                }
            )
        }
    }

    /** Karakter profillerindeki seiyuu/oyuncu adları için AniList + kişi infobox yedeği. */
    suspend fun enrichVoiceActorNamesFromBangumi(
        actors: List<KitsugiVoiceActor>,
        background: Boolean = false,
        cacheOnly: Boolean = false
    ): List<KitsugiVoiceActor> {
        if (actors.isEmpty()) return actors
        val resolved = BangumiEntityNameResolver.resolve(
            actors
                .filter { needsLatinName(it.name, it.romanizedName, it.englishName) }
                .map {
                    BangumiEntityNameResolver.Request(
                        BangumiEntityNameResolver.Kind.PERSON,
                        it.id,
                        it.nativeName,
                        it.name
                    )
                },
            background,
            cacheOnly
        )
        val preEnriched = actors.map { actor ->
            val variant = resolved[
                BangumiEntityNameResolver.cacheKey(BangumiEntityNameResolver.Kind.PERSON, actor.id)
            ] ?: return@map actor
            actor.copy(
                name = latinDisplayName(actor.name, variant),
                romanizedName = usableLatinName(actor.romanizedName) ?: variant.romaji,
                nativeName = actor.nativeName ?: variant.native,
                englishName = usableLatinName(actor.englishName) ?: variant.english
            )
        }
        val lookups = preEnriched
            .filter { needsLatinName(it.name, it.romanizedName, it.englishName) }
            .map { EntityNameLookup("persons", it.id) }
        val names = fetchEntityLocalizedNames(lookups)
        return preEnriched.map { actor ->
            val localized = names[EntityNameLookup("persons", actor.id).cacheKey]
            actor.copy(
                name = localizedDisplay(actor.name, localized),
                romanizedName = usableLatinName(actor.romanizedName) ?: localized?.romaji,
                nativeName = actor.nativeName ?: localized?.native,
                englishName = usableLatinName(actor.englishName) ?: localized?.english
            )
        }
    }

    /** Ekip adlarında AniList birebir eşleşmesi + p1 infobox'taki İngilizce/romaji alanları. */
    suspend fun enrichStaffNamesFromBangumi(
        staff: List<KitsugiStaff>,
        background: Boolean = false,
        cacheOnly: Boolean = false
    ): List<KitsugiStaff> {
        if (staff.isEmpty()) return staff
        val resolved = BangumiEntityNameResolver.resolve(
            staff
                .filter { needsLatinName(it.name, it.romanizedName, it.englishName) }
                .map {
                    BangumiEntityNameResolver.Request(
                        BangumiEntityNameResolver.Kind.PERSON,
                        it.id,
                        it.nativeName,
                        it.name
                    )
                },
            background,
            cacheOnly
        )
        val preEnriched = staff.map { member ->
            val variant = resolved[
                BangumiEntityNameResolver.cacheKey(BangumiEntityNameResolver.Kind.PERSON, member.id)
            ] ?: return@map member
            member.copy(
                name = latinDisplayName(member.name, variant),
                romanizedName = usableLatinName(member.romanizedName) ?: variant.romaji,
                nativeName = member.nativeName ?: variant.native,
                englishName = usableLatinName(member.englishName) ?: variant.english
            )
        }
        val lookups = preEnriched
            .filter { needsLatinName(it.name, it.romanizedName, it.englishName) }
            .map { EntityNameLookup("persons", it.id) }
        val names = fetchEntityLocalizedNames(lookups)
        return preEnriched.map { member ->
            val localized = names[EntityNameLookup("persons", member.id).cacheKey]
            member.copy(
                name = localizedDisplay(member.name, localized),
                romanizedName = usableLatinName(member.romanizedName) ?: localized?.romaji,
                nativeName = member.nativeName ?: localized?.native,
                englishName = usableLatinName(member.englishName) ?: localized?.english
            )
        }
    }

    /**
     * Kişi detay sayfasındaki karakter rolleri ve yapımlar için Latin ad zenginleştirmesi.
     *
     * Bangumi `/v0/persons/{id}/characters` ve `/v0/persons/{id}/subjects` uçları infobox
     * döndürmez; bu yüzden karakter ve yapım adları CJK (Japonca/Çince) kalır. Bu fonksiyon:
     *  1. Karakter adları için karakter detaylarını (`/v0/characters/{id}`) çeker — infobox'tan
     *     romaji/İngilizce alınır (sınırlı sayıda, önbellekli).
     *  2. Yapım başlıkları için [BangumiTitleCache] önbelleğine bakar — kayıt daha önce
     *     çözülmüşse ağ isteği yapılmaz.
     *
     * Zaman aşımı olursa mevcut (CJK) değerler korunur; ekran boş kalmaz.
     */
    suspend fun enrichStaffDetailNames(
        detail: KitsugiStaffDetail,
        background: Boolean = false,
        cacheOnly: Boolean = false
    ): KitsugiStaffDetail {
        if (detail.characterRoles.isEmpty() && detail.mediaWorks.isEmpty()) return detail

        // 1a) Karakter ve kişi adları için AniList birebir ad eşleşmesi (kalıcı önbellekli).
        // Bangumi karakter/kişi infobox'ında romaji çok seyrek olduğundan asıl çözüm burasıdır.
        val nameRequests = buildList {
            if (needsLatinName(detail.name, detail.romanizedName, detail.englishName)) {
                add(
                    BangumiEntityNameResolver.Request(
                        BangumiEntityNameResolver.Kind.PERSON,
                        detail.id,
                        detail.nativeName,
                        detail.name
                    )
                )
            }
            detail.characterRoles
                .filter { needsLatinName(it.characterName, it.characterRomanizedName, it.characterEnglishName) }
                .forEach {
                    add(
                        BangumiEntityNameResolver.Request(
                            BangumiEntityNameResolver.Kind.CHARACTER,
                            it.characterId,
                            it.characterNativeName,
                            it.characterName
                        )
                    )
                }
        }
        val resolvedNames = BangumiEntityNameResolver.resolve(nameRequests, background, cacheOnly)
        val heroVariant = resolvedNames[
            BangumiEntityNameResolver.cacheKey(BangumiEntityNameResolver.Kind.PERSON, detail.id)
        ]
        val namedDetail = if (
            heroVariant != null && needsLatinName(detail.name, detail.romanizedName, detail.englishName)
        ) {
            detail.copy(
                name = latinDisplayName(detail.name, heroVariant),
                romanizedName = usableLatinName(detail.romanizedName) ?: heroVariant.romaji,
                englishName = usableLatinName(detail.englishName) ?: heroVariant.english,
                nativeName = detail.nativeName ?: heroVariant.native
            )
        } else detail

        // 1b) Kalanlar için Bangumi infobox yedeği
        val characterLookups = namedDetail.characterRoles
            .filter { role ->
                val v = resolvedNames[
                    BangumiEntityNameResolver.cacheKey(BangumiEntityNameResolver.Kind.CHARACTER, role.characterId)
                ]
                v == null && needsLatinName(role.characterName, role.characterRomanizedName, role.characterEnglishName)
            }
            .map { EntityNameLookup("characters", it.characterId) }
        val characterNames = if (characterLookups.isNotEmpty()) {
            fetchEntityLocalizedNames(characterLookups)
        } else emptyMap()

        // 2) Yapım başlıkları için ortak çözümleyici: önbellek → v0 infobox → AniList.
        val titleRequests = buildList {
            namedDetail.characterRoles.forEach { role ->
                BangumiSubjectTitleResolver.Request.fromRow(role.mediaId, role.mediaTitle, role.mediaType)?.let(::add)
            }
            namedDetail.mediaWorks.forEach { work ->
                BangumiSubjectTitleResolver.Request.fromRow(work.mediaId, work.mediaTitle, work.mediaType)?.let(::add)
            }
        }
        val subjectTitles = BangumiSubjectTitleResolver.resolve(titleRequests, background, cacheOnly)

        // 3) Karakter rollerini zenginleştir
        val enrichedRoles = namedDetail.characterRoles.map { role ->
            val variant = resolvedNames[
                BangumiEntityNameResolver.cacheKey(BangumiEntityNameResolver.Kind.CHARACTER, role.characterId)
            ]
            val localized = characterNames[EntityNameLookup("characters", role.characterId).cacheKey]
            val rawSubjectId = BangumiIdNamespace.rawIdOrNull(role.mediaId)
            val latin = rawSubjectId?.let { subjectTitles[it] }
            val subjectLatin = latin?.let { usableLatinName(it.romaji) ?: usableLatinName(it.english) }
            val disp = variant?.let { latinDisplayName(role.characterName, it) }
                ?: localizedDisplay(role.characterName, localized)
            role.copy(
                characterName = disp,
                characterRomanizedName = usableLatinName(role.characterRomanizedName)
                    ?: variant?.romaji ?: localized?.romaji,
                characterNativeName = role.characterNativeName ?: variant?.native ?: localized?.native,
                characterEnglishName = usableLatinName(role.characterEnglishName)
                    ?: variant?.english ?: localized?.english,
                // Önbellekten/istekten gelen Latin başlık varsa onu kullan
                mediaTitle = subjectLatin ?: role.mediaTitle,
                mediaTitleRomaji = role.mediaTitleRomaji ?: latin?.romaji,
                mediaTitleEnglish = role.mediaTitleEnglish ?: latin?.english
            )
        }

        // 4) Yapım listesini zenginleştir
        val enrichedWorks = namedDetail.mediaWorks.map { work ->
            val rawSubjectId = BangumiIdNamespace.rawIdOrNull(work.mediaId)
            val latin = rawSubjectId?.let { subjectTitles[it] }
            if (latin != null && (latin.romaji != null || latin.english != null)) {
                val subjectLatin = usableLatinName(latin.romaji) ?: usableLatinName(latin.english)
                work.copy(
                    mediaTitle = subjectLatin ?: work.mediaTitle,
                    titleRomaji = work.titleRomaji ?: latin.romaji,
                    titleEnglish = work.titleEnglish ?: latin.english
                )
            } else work
        }

        return namedDetail.copy(
            characterRoles = enrichedRoles,
            mediaWorks = enrichedWorks
        )
    }

    /**
     * Karakter detay sayfasındaki "Yapımlar" listesi için Latin başlık zenginleştirmesi.
     *
     * Bangumi `/v0/characters/{id}/subjects` ucu yalnızca özgün (Japonca) ve Çince ad
     * döndürür; bu fonksiyon [BangumiSubjectTitleResolver] ile romaji/İngilizce karşılığı
     * çözer ve `titleRomaji` / `titleEnglish` alanlarını doldurur. Böylece arayüz, seçili
     * başlık diline (İngilizce / Romaji) uygun başlığı gösterir. Karakterin kendi adı ve
     * seslendirmen adları zaten infobox köprüsüyle zenginleştirilir.
     */
    suspend fun enrichCharacterDetailNames(
        detail: KitsugiCharacterDetail,
        background: Boolean = false,
        cacheOnly: Boolean = false
    ): KitsugiCharacterDetail {
        // 1) Karakterin kendi adı ve seslendirmen adları: AniList birebir ad eşleşmesi
        //    (kalıcı önbellekli). Bangumi karakter infobox'ında romaji çok seyrektir.
        val nameRequests = buildList {
            if (needsLatinName(detail.name, detail.romanizedName, detail.englishName)) {
                add(
                    BangumiEntityNameResolver.Request(
                        BangumiEntityNameResolver.Kind.CHARACTER,
                        detail.id,
                        detail.nativeName,
                        detail.name
                    )
                )
            }
            detail.voiceActors
                .filter { needsLatinName(it.name, it.romanizedName, it.englishName) }
                .forEach {
                    add(
                        BangumiEntityNameResolver.Request(
                            BangumiEntityNameResolver.Kind.PERSON,
                            it.id,
                            it.nativeName,
                            it.name
                        )
                    )
                }
        }
        val resolvedNames = BangumiEntityNameResolver.resolve(nameRequests, background, cacheOnly)
        val heroVariant = resolvedNames[
            BangumiEntityNameResolver.cacheKey(BangumiEntityNameResolver.Kind.CHARACTER, detail.id)
        ]
        val namedDetail = if (
            heroVariant != null && needsLatinName(detail.name, detail.romanizedName, detail.englishName)
        ) {
            detail.copy(
                name = latinDisplayName(detail.name, heroVariant),
                romanizedName = usableLatinName(detail.romanizedName) ?: heroVariant.romaji,
                englishName = usableLatinName(detail.englishName) ?: heroVariant.english,
                nativeName = detail.nativeName ?: heroVariant.native
            )
        } else detail
        val namedVoices = namedDetail.voiceActors.map { actor ->
            val variant = resolvedNames[
                BangumiEntityNameResolver.cacheKey(BangumiEntityNameResolver.Kind.PERSON, actor.id)
            ] ?: return@map actor
            actor.copy(
                name = latinDisplayName(actor.name, variant),
                romanizedName = usableLatinName(actor.romanizedName) ?: variant.romaji,
                englishName = usableLatinName(actor.englishName) ?: variant.english,
                nativeName = actor.nativeName ?: variant.native
            )
        }
        val withNames = namedDetail.copy(voiceActors = namedVoices)
        if (withNames.mediaAppearances.isEmpty()) return withNames

        // 2) "Yapımlar" başlıkları için ortak subject çözümleyici.
        val titleRequests = withNames.mediaAppearances.mapNotNull { appearance ->
            BangumiSubjectTitleResolver.Request.fromRow(appearance.mediaId, appearance.title, appearance.mediaType)
        }
        val subjectTitles = BangumiSubjectTitleResolver.resolve(titleRequests, background, cacheOnly)
        val enriched = withNames.mediaAppearances.map { appearance ->
            val rawSubjectId = BangumiIdNamespace.rawIdOrNull(appearance.mediaId)
            val latin = rawSubjectId?.let { subjectTitles[it] } ?: return@map appearance
            val latinDisplay = usableLatinName(latin.romaji) ?: usableLatinName(latin.english)
            appearance.copy(
                title = latinDisplay ?: appearance.title,
                titleRomaji = appearance.titleRomaji ?: latin.romaji,
                titleEnglish = appearance.titleEnglish ?: latin.english
            )
        }
        return withNames.copy(mediaAppearances = enriched)
    }

    private fun needsLatinName(name: String, romanized: String?, english: String?): Boolean =
        PreferenceHelpers.hasCjkCharacters(name) &&
            usableLatinName(romanized) == null && usableLatinName(english) == null

    private fun usableLatinName(name: String?): String? = name?.trim()?.takeIf {
        PreferenceHelpers.isLatinText(it)
    }

    private fun localizedDisplay(original: String, localized: BangumiLocalizedName?): String =
        localized?.display?.takeIf { usableLatinName(it) != null } ?: original

    /** AniList birebir eşleşmesinden gelen ad varyantıyla Latin gösterim adı üretir. */
    private fun latinDisplayName(original: String, variants: BangumiEntityNameResolver.NameVariants?): String =
        usableLatinName(variants?.romaji) ?: usableLatinName(variants?.english) ?: original

    private suspend fun fetchEntityLocalizedNames(
        requested: List<EntityNameLookup>
    ): Map<String, BangumiLocalizedName> = coroutineScope {
        val resolved = ConcurrentHashMap<String, BangumiLocalizedName>()
        withTimeoutOrNull(ENTITY_NAME_BATCH_TIMEOUT_MS) {
            requested.distinctBy { it.cacheKey }.take(ENTITY_NAME_LOOKUP_LIMIT)
                .map { lookup ->
                    async(Dispatchers.IO) {
                        val localized = try {
                            enrichmentRequestSemaphore.withPermit {
                                withTimeoutOrNull(ENTITY_NAME_LOOKUP_TIMEOUT_MS) {
                                    loadEntityLocalizedName(lookup)
                                }
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            null
                        }
                        localized?.let { resolved[lookup.cacheKey] = it }
                    }
                }
                .awaitAll()
        }
        resolved.toMap()
    }

    private suspend fun loadEntityLocalizedName(lookup: EntityNameLookup): BangumiLocalizedName? {
        val now = System.currentTimeMillis()
        entityNameCache[lookup.cacheKey]?.let { cached ->
            val ttl = if (cached.name == null) ENTITY_NAME_NEGATIVE_CACHE_TTL_MS else ENTITY_NAME_CACHE_TTL_MS
            if (now - cached.cachedAtMs < ttl) return cached.name
        }
        val root = try {
            p1("/${lookup.kind}/${lookup.id}")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        val localized = root?.let {
            val native = it.strOrNull("name")
            val chinese = it.strOrNull("nameCn") ?: it.strOrNull("name_cn")
            BangumiNameLocalizer.entityFromInfobox(native, chinese, parseP1Infobox(it.optJSONArray("infobox")))
        }?.takeIf { usableLatinName(it.romaji) != null || usableLatinName(it.english) != null }
        if (entityNameCache.size > 512) entityNameCache.clear()
        entityNameCache[lookup.cacheKey] = EntityNameCacheEntry(localized, now)
        return localized
    }

    /** İlişkili yapımlar (`/p1/subjects/{id}/relations`). */
    suspend fun fetchRelations(stableOrRawId: Int, mediaType: MediaType): List<KitsugiRelation> =
        withContext(Dispatchers.IO) {
            val rawId = rawIdOf(stableOrRawId) ?: return@withContext emptyList()
            val root = p1("/subjects/$rawId/relations", mapOf("limit" to "100")) ?: return@withContext emptyList()
            enrichRelationTitles(parseRelations(root))
        }

    /** `/p1/subjects/{id}/relations` yanıtı → ilişkili yapımlar. */
    internal fun parseRelations(root: JSONObject): List<KitsugiRelation> =
        root.optJSONArray("data").objects().mapNotNull { item ->
            val subject = item.optJSONObject("subject") ?: return@mapNotNull null
            val relation = item.optJSONObject("relation")
            val label = relationLabel(relation?.strOrNull("cn"), relation?.strOrNull("en"))
            subject.toRelation(label)
        }.distinctBy { it.malId }

    /** Öneriler (`/p1/subjects/{id}/recs`; sunucu en fazla 10 kayıt verir). */
    suspend fun fetchRecommendations(stableOrRawId: Int, mediaType: MediaType): List<KitsugiRelation> =
        withContext(Dispatchers.IO) {
            val rawId = rawIdOf(stableOrRawId) ?: return@withContext emptyList()
            val root = p1("/subjects/$rawId/recs", mapOf("limit" to "10")) ?: return@withContext emptyList()
            enrichRelationTitles(parseRecommendations(root))
        }

    /**
     * İlişki/öneri başlıklarını tam条目 kaydıyla zenginleştirir. p1 liste uçlarındaki slim
     * subject nesneleri infobox içermez; bu yüzden başlıklar özgün (kanji) adla kalıyordu.
     *
     * İki kademe çalışır:
     *  1. [BangumiTitleCache] — kayıt daha önce herhangi bir yerde çözüldüyse Latin ad ağ
     *     isteği olmadan yerleştirilir.
     *  2. Yalnızca **hâlâ CJK görünen** kayıtlar için `/v0/subjects/{id}` çekilir; bulunan
     *     İngilizce/romaji ad hem başlığa yazılır hem de önbelleğe kalıcılanır.
     *
     * Eskiden listenin tamamı (64 kayıt) indirilmeye çalışıldığı için süre bütçesi erken doluyor
     * ve İngilizce adı mevcut olan kayıtlar ekranda Çince kalıyordu. Tek bir kayıt isteği
     * başarısızsa ya da gecikirse ilgili kayıt orijinal haliyle döner.
     */
    internal suspend fun enrichRelationTitles(
        relations: List<KitsugiRelation>,
        background: Boolean = false,
        cacheOnly: Boolean = false
    ): List<KitsugiRelation> {
        if (relations.isEmpty()) return relations

        val cached = relations.map { relation -> withCachedLatinTitle(relation) }
        val pending = cached.filter { needsLatinTitle(it) }.take(RELATION_TITLE_ENRICH_LIMIT)
        if (pending.isEmpty() || cacheOnly) return cached

        // p1 liste uçları slim subject döndürür (infobox yok); bu yüzden başlıklar ortak
        // çözümleyiciyle çözülür: önbellek → AniList toplu araması → subject infobox → TMDB.
        // Eski kod yalnızca infobox'u denediği için İngilizce/romaji adı infobox'ta olmayan
        // kayıtlar (ilişkiler/öneriler sekmesi) kalıcı olarak CJK kalıyordu.
        val requests = pending.mapNotNull { relation ->
            BangumiSubjectTitleResolver.Request.fromRow(
                mediaId = relation.malId,
                currentTitle = relation.title,
                mediaTypeKey = relation.mediaType.name,
                subjectType = relation.bangumiSubjectType
            )
        }
        val resolved = BangumiSubjectTitleResolver.resolve(requests, background = background)
        return cached.map { relation ->
            val raw = BangumiIdNamespace.rawIdFromStable(relation.malId) ?: return@map relation
            val latin = resolved[raw] ?: BangumiTitleCache.get(raw) ?: return@map relation
            applyResolvedLatinTitle(relation, latin)
        }
    }

    /**
     * Çözülen Latin başlık varyantlarını ilişki kaydına işler. Gösterim katmanı seçimi
     * `getDisplayTitle(titleLanguage)` yaptığı için burada yalnızca varyantlar doldurulur;
     * `title` alanı Romaji zincirinin Latin yedeği olarak güncellenir.
     */
    internal fun applyResolvedLatinTitle(
        relation: KitsugiRelation,
        latin: BangumiTitleCache.LatinTitles
    ): KitsugiRelation {
        val romaji = usableLatinName(latin.romaji)
        val english = usableLatinName(latin.english)
        if (romaji == null && english == null) return relation
        return relation.copy(
            title = romaji ?: english ?: relation.title,
            titleRomaji = relation.titleRomaji ?: romaji,
            titleEnglish = relation.titleEnglish ?: english,
            titleJapanese = relation.titleJapanese ?: latin.native
        )
    }

    /** Başlıkta Latin bir karşılık biliniyorsa ilişki kaydına işler. */
    private fun withCachedLatinTitle(relation: KitsugiRelation): KitsugiRelation {
        val raw = BangumiIdNamespace.rawIdFromStable(relation.malId) ?: return relation
        val cached = BangumiTitleCache.get(raw)
        val latin = BangumiTitleCache.latinTitleFor(raw, relation.title)
        val relationRomaji = relation.titleRomaji
            ?: relation.title.takeIf { PreferenceHelpers.isLatinText(it) }
        val relationHasReliableEnglish = BangumiSubjectTitleResolver.hasReliableEnglish(
            BangumiTitleCache.LatinTitles(relationRomaji, relation.titleEnglish, relation.titleJapanese)
        )
        val cachedEnglish = cached?.english?.takeIf {
            BangumiSubjectTitleResolver.hasReliableEnglish(cached)
        }
        val english = if (relationHasReliableEnglish) relation.titleEnglish else cachedEnglish
        val updated = relation.copy(
            title = latin ?: relation.title,
            titleEnglish = english,
            titleJapanese = relation.titleJapanese ?: cached?.native,
            titleRomaji = relation.titleRomaji ?: cached?.romaji
        )
        return updated.takeUnless { it == relation } ?: relation
    }

    /** Çiplerde CJK başlık görünüyor ve kayıtta Latin varyant yoksa ek arama yapılır. */
    private fun needsLatinTitle(relation: KitsugiRelation): Boolean =
        usableLatinName(relation.title) == null &&
            usableLatinName(relation.titleEnglish) == null &&
            usableLatinName(relation.titleRomaji) == null

    /** `/p1/subjects/{id}/recs` yanıtı → öneriler. */
    internal fun parseRecommendations(root: JSONObject): List<KitsugiRelation> =
        root.optJSONArray("data").objects().mapNotNull { item ->
            item.optJSONObject("subject")?.toRelation("Recommendation")
        }.distinctBy { it.malId }

    private fun JSONObject.toRelation(relationType: String): KitsugiRelation? {
        val type = optInt("type", 0)
        // Bilinmeyen türler ve oyun/müzik kayıtları hiçbir zaman Anime gibi gösterilmez.
        if (!BangumiIdNamespace.supportsSubjectType(type)) return null
        val rawId = optInt("id", 0).takeIf { it > 0 } ?: return null
        val stable = stableIdOf(rawId) ?: return null
        val localizedTitle = localizedName()
        // Liste uçlarında infobox alanı dönmez; dil varyantları önbellekten ayrı alınır.
        val cachedTitles = BangumiTitleCache.get(rawId)
        val cachedLatin = BangumiTitleCache.latinTitleFor(rawId, localizedTitle.display)
        val cachedEnglish = cachedTitles?.english?.takeIf {
            BangumiSubjectTitleResolver.hasReliableEnglish(cachedTitles)
        }
        return KitsugiRelation(
            malId = stable,
            title = cachedLatin
                ?: localizedTitle.display.ifBlank { "Bangumi #$rawId" },
            relationType = relationType,
            imageUrl = optJSONObject("images").image("common", "medium", "large", "grid", "small"),
            mediaType = BangumiIdNamespace.mediaTypeFor(type, optString("platform").takeIf { it.isNotBlank() }),
            source = SOURCE,
            titleEnglish = localizedTitle.english ?: cachedEnglish,
            titleJapanese = localizedTitle.native ?: cachedTitles?.native,
            titleRomaji = localizedTitle.romaji ?: cachedTitles?.romaji ?: cachedLatin,
            isAdult = optBoolean("nsfw", false),
            bangumiSubjectType = type
        )
    }

    /** Puan dağılımı + koleksiyon durumu + sıralama (v0 yanıtından; ek istek yok). */
    suspend fun fetchStats(stableOrRawId: Int, mediaType: MediaType): KitsugiStats? =
        withContext(Dispatchers.IO) {
            val rawId = rawIdOf(stableOrRawId) ?: return@withContext null
            val subject = loadSubject(rawId) ?: return@withContext null
            buildStats(subject)
        }

    /** v0 条目 yanıtından istatistik (puan dağılımı + koleksiyon + sıralama). */
    internal fun buildStats(subject: BangumiSubject): KitsugiStats? {
        val collection = subject.collection
        val rating = subject.rating
        val scores = (1..10).map { KitsugiScoreStat(it, rating?.count?.get(it) ?: 0) }
        val hasScores = scores.any { it.amount > 0 }
        val rankings = rating?.rank?.takeIf { it > 0 }?.let { rank ->
            listOf(KitsugiRanking(rank = rank, type = "RATED", context = "Bangumi highest rated all time", allTime = true))
        }.orEmpty()
        if (collection == null && !hasScores && rankings.isEmpty()) return null
        return KitsugiStats(
            watching = collection?.doing?.takeIf { it > 0 },
            completed = collection?.collect?.takeIf { it > 0 },
            planned = collection?.wish?.takeIf { it > 0 },
            dropped = collection?.dropped?.takeIf { it > 0 },
            paused = collection?.onHold?.takeIf { it > 0 },
            scoreDistribution = if (hasScores) scores else emptyList(),
            rankings = rankings
        )
    }

    /**
     * Yorumlar: uzun incelemeler (`/reviews`, tam metin `/blogs/{id}`) + kısa kullanıcı yorumları
     * (`/comments`, puanlı). Sayfa başına 10 inceleme + 20 yorum.
     */
    suspend fun fetchReviews(stableOrRawId: Int, page: Int): List<KitsugiReview> =
        withContext(Dispatchers.IO) {
            val rawId = rawIdOf(stableOrRawId) ?: return@withContext emptyList()
            val pageIndex = (page - 1).coerceAtLeast(0)
            coroutineScope {
                val reviewsDef = async {
                    val root = p1("/subjects/$rawId/reviews", mapOf("limit" to "10", "offset" to (pageIndex * 10).toString()))
                    val items = root?.optJSONArray("data").objects()
                    // Tam metinler paralel ve süre sınırlı çekilir; gelmezse özet gösterilir.
                    items.map { item ->
                        async {
                            val entry = item.optJSONObject("entry")
                            val entryId = entry?.optInt("id", 0) ?: 0
                            val body = if (entryId > 0) {
                                withTimeoutOrNull(4_000L) { p1("/blogs/$entryId") }?.strOrNull("content")?.let { cleanMarkup(it) }
                            } else null
                            reviewFromBlog(item, body)
                        }
                    }.awaitAll().filterNotNull()
                }
                val commentsDef = async {
                    val root = p1("/subjects/$rawId/comments", mapOf("limit" to "20", "offset" to (pageIndex * 20).toString()))
                    root?.optJSONArray("data").objects().mapNotNull { reviewFromComment(it) }
                }
                (reviewsDef.await() + commentsDef.await())
            }
        }

    internal fun reviewFromBlog(item: JSONObject, fullBody: String?): KitsugiReview? {
        val entry = item.optJSONObject("entry") ?: return null
        val user = item.optJSONObject("user") ?: entry.optJSONObject("user")
        val title = entry.strOrNull("title").orEmpty()
        val summary = entry.strOrNull("summary")?.let { cleanMarkup(it) }.orEmpty()
        val body = fullBody?.let { cleanMarkup(it) }?.takeIf { it.isNotBlank() } ?: summary
        if (title.isEmpty() && body.isEmpty()) return null
        return KitsugiReview(
            id = null,
            userId = null,
            username = user?.strOrNull("nickname") ?: user?.strOrNull("username") ?: "Bangumi",
            avatarUrl = user?.optJSONObject("avatar").image("medium", "large", "small"),
            score = null,
            summary = (if (title.isNotEmpty()) title else summary).take(240),
            fullText = (if (title.isNotEmpty() && body.isNotEmpty()) "$title\n\n$body" else body.ifEmpty { title }),
            dateText = epochDate(entry.optLong("createdAt", 0L)),
            helpfulCount = entry.optInt("replies", 0).takeIf { it > 0 },
            source = "bangumi"
        )
    }

    internal fun reviewFromComment(item: JSONObject): KitsugiReview? {
        val text = item.strOrNull("comment")?.let { cleanMarkup(it) }.orEmpty()
        if (text.isBlank()) return null
        val user = item.optJSONObject("user")
        return KitsugiReview(
            id = null,
            userId = null,
            username = user?.strOrNull("nickname") ?: user?.strOrNull("username") ?: "Bangumi",
            avatarUrl = user?.optJSONObject("avatar").image("medium", "large", "small"),
            score = item.optInt("rate", 0).takeIf { it in 1..10 },
            summary = text.take(240),
            fullText = text,
            dateText = epochDate(item.optLong("updatedAt", 0L)),
            source = "bangumi"
        )
    }

    /** Ana hikâye bölümleri (`/p1/subjects/{id}/episodes?type=0`). */
    suspend fun fetchEpisodes(stableOrRawId: Int): List<KitsugiStreamingEpisode> =
        withContext(Dispatchers.IO) {
            val rawId = rawIdOf(stableOrRawId) ?: return@withContext emptyList()
            val root = p1("/subjects/$rawId/episodes", mapOf("type" to "0", "limit" to "1000")) ?: return@withContext emptyList()
            parseEpisodes(root)
        }

    /** `/p1/subjects/{id}/episodes` yanıtı → bölüm listesi (yalnızca ana hikâye, tekil numara). */
    internal fun parseEpisodes(root: JSONObject): List<KitsugiStreamingEpisode> {
        val seen = HashSet<Int>()
        return root.optJSONArray("data").objects().mapNotNull { ep ->
            val id = ep.optInt("id", 0)
            val sort = ep.optDouble("sort", Double.NaN)
            if (sort.isNaN() || sort <= 0.0) return@mapNotNull null
            val number = sort.toInt()
            if (number <= 0 || !seen.add(number)) return@mapNotNull null
            // Bölüm yanıtında ayrı English/romaji alanı yok. Özgün ad Latin harfliyse o kullanılır;
            // CJK ise Çince yerelleştirme deneme sırasına girer (Latin ad, CJK'da tercih edilir).
            // İkisi de CJK ise özgün ad olduğu gibi kalır — uydurma çeviri yapılmaz.
            val rawName = ep.strOrNull("name")
            val rawNameCn = ep.strOrNull("nameCN")
            val latinName = listOfNotNull(rawName, rawNameCn).firstOrNull {
                PreferenceHelpers.isLatinText(it)
            }
            val name = latinName ?: rawName ?: rawNameCn
            val defaultTitle = if (isTurkish()) "Bölüm $number" else "Episode $number"
            KitsugiStreamingEpisode(
                title = if (name != null) "#$number – $name" else KitsugiStrings.getOrFallback(R.string.inline_episode_1_s_dc662be, defaultTitle, number),
                thumbnail = null,
                url = if (id > 0) "${BangumiApiClient.SITE_BASE}/ep/$id" else null,
                site = "Bangumi",
                seasonNumber = 1,
                episodeNumber = number
            )
        }.sortedBy { it.episodeNumber ?: 0 }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 4) KARAKTER / KİŞİ DETAY SAYFALARI
    // ═════════════════════════════════════════════════════════════════════════

    suspend fun fetchCharacterDetail(characterId: Int): KitsugiCharacterDetail? =
        withContext(Dispatchers.IO) {
            if (characterId <= 0) return@withContext null
            coroutineScope {
                val rootDef = async { p1("/characters/$characterId") }
                val castsDef = async { p1("/characters/$characterId/casts", mapOf("limit" to "40")) }
                val root = rootDef.await() ?: return@coroutineScope null
                val casts = castsDef.await()?.optJSONArray("data").objects()
                val info = parseP1Infobox(root.optJSONArray("infobox"))

                val actors = casts.flatMap { it.optJSONArray("casts").objects() }.mapNotNull { cast ->
                    val person = cast.optJSONObject("person") ?: return@mapNotNull null
                    val id = person.optInt("id", 0).takeIf { it > 0 } ?: return@mapNotNull null
                    val personName = person.localizedName()
                    KitsugiVoiceActor(
                        id = id,
                        name = personName.display,
                        language = castLanguage(cast.optInt("relation", 0)),
                        imageUrl = person.optJSONObject("images").image("medium", "large", "grid", "small"),
                        source = SOURCE,
                        romanizedName = personName.romaji,
                        nativeName = personName.native,
                        englishName = personName.english
                    )
                }.distinctBy { it.id }
                val localizedActors = enrichVoiceActorNamesFromBangumi(actors)

                val appearances = casts.mapNotNull { item ->
                    val subject = item.optJSONObject("subject") ?: return@mapNotNull null
                    val type = subject.optInt("type", 2)
                    if (type != BangumiApiClient.SubjectType.ANIME && type != BangumiApiClient.SubjectType.BOOK &&
                        type != BangumiApiClient.SubjectType.REAL
                    ) return@mapNotNull null
                    val stable = stableIdOf(subject.optInt("id", 0)) ?: return@mapNotNull null
                    val subjectName = subject.localizedName()
                    KitsugiCharacterMediaAppearance(
                        mediaId = stable,
                        title = subjectName.display,
                        imageUrl = subject.optJSONObject("images").image("common", "medium", "large", "grid"),
                        mediaType = if (type == BangumiApiClient.SubjectType.BOOK) "MANGA" else "ANIME",
                        characterRole = characterRole(item.optInt("type", 2)).toTurkishCharacterRole(),
                        source = SOURCE,
                        titleEnglish = subjectName.english,
                        titleJapanese = subjectName.native,
                        titleRomaji = subjectName.romaji
                    )
                }.distinctBy { it.mediaId }

                val characterName = BangumiNameLocalizer.entityFromInfobox(
                    root.strOrNull("name"),
                    root.strOrNull("nameCN") ?: root.strOrNull("name_cn"),
                    info
                )
                KitsugiCharacterDetail(
                    id = characterId,
                    name = characterName.display.ifBlank { "Bangumi #$characterId" },
                    nativeName = characterName.native,
                    alternativeNames = characterName.alternatives,
                    imageUrl = root.optJSONObject("images").image("large", "medium", "grid", "small"),
                    gender = info["性别"]?.firstOrNull()?.let { genderText(it) },
                    age = info["年龄"]?.firstOrNull(),
                    birthday = info["生日"]?.firstOrNull(),
                    bloodType = info["血型"]?.firstOrNull(),
                    biography = root.strOrNull("summary")?.let { cleanMarkup(it) },
                    voiceActors = localizedActors,
                    mediaAppearances = appearances,
                    source = SOURCE,
                    romanizedName = characterName.romaji,
                    englishName = characterName.english
                )
            }
        }

    suspend fun fetchStaffDetail(personId: Int): KitsugiStaffDetail? =
        withContext(Dispatchers.IO) {
            if (personId <= 0) return@withContext null
            coroutineScope {
                val rootDef = async { p1("/persons/$personId") }
                val worksDef = async { p1("/persons/$personId/works", mapOf("limit" to "60")) }
                val castsDef = async { p1("/persons/$personId/casts", mapOf("limit" to "60")) }
                val root = rootDef.await() ?: return@coroutineScope null
                val info = parseP1Infobox(root.optJSONArray("infobox"))

                val works = worksDef.await()?.optJSONArray("data").objects().mapNotNull { item ->
                    val subject = item.optJSONObject("subject") ?: return@mapNotNull null
                    val type = subject.optInt("type", 2)
                    if (type != BangumiApiClient.SubjectType.ANIME && type != BangumiApiClient.SubjectType.BOOK &&
                        type != BangumiApiClient.SubjectType.REAL
                    ) return@mapNotNull null
                    val stable = stableIdOf(subject.optInt("id", 0)) ?: return@mapNotNull null
                    val role = item.optJSONArray("positions").objects()
                        .mapNotNull { pos -> pos.optJSONObject("type")?.let { it.strOrNull("en") ?: it.strOrNull("cn") } }
                        .distinct().take(3).joinToString(", ").ifBlank { "Staff" }
                    val subjectName = subject.localizedName()
                    KitsugiStaffMediaWork(
                        mediaId = stable,
                        mediaTitle = subjectName.display,
                        mediaImageUrl = subject.optJSONObject("images").image("common", "medium", "large", "grid"),
                        mediaType = if (type == BangumiApiClient.SubjectType.BOOK) "MANGA" else "ANIME",
                        staffRole = role.toTurkishStaffRole(),
                        source = SOURCE,
                        titleEnglish = subjectName.english,
                        titleJapanese = subjectName.native,
                        titleRomaji = subjectName.romaji
                    )
                }.distinctBy { it.mediaId }

                val characterRoles = castsDef.await()?.optJSONArray("data").objects().flatMap { item ->
                    val character = item.optJSONObject("character") ?: return@flatMap emptyList()
                    val characterId = character.optInt("id", 0).takeIf { it > 0 } ?: return@flatMap emptyList()
                    item.optJSONArray("relations").objects().mapNotNull { relation ->
                        val subject = relation.optJSONObject("subject") ?: return@mapNotNull null
                        val type = subject.optInt("type", 2)
                        if (type != BangumiApiClient.SubjectType.ANIME && type != BangumiApiClient.SubjectType.BOOK &&
                            type != BangumiApiClient.SubjectType.REAL
                        ) return@mapNotNull null
                        val stable = stableIdOf(subject.optInt("id", 0)) ?: return@mapNotNull null
                        val characterName = character.localizedName()
                        val subjectName = subject.localizedName()
                        KitsugiStaffCharacterRole(
                            characterId = characterId,
                            characterName = characterName.display,
                            characterImageUrl = character.optJSONObject("images").image("medium", "large", "grid", "small"),
                            characterSource = SOURCE,
                            mediaId = stable,
                            mediaTitle = subjectName.display,
                            mediaImageUrl = subject.optJSONObject("images").image("common", "medium", "large", "grid"),
                            mediaType = if (type == BangumiApiClient.SubjectType.BOOK) "MANGA" else "ANIME",
                            characterRole = characterRole(relation.optInt("type", 2)).toTurkishCharacterRole(),
                            mediaSource = SOURCE,
                            characterRomanizedName = characterName.romaji,
                            characterNativeName = characterName.native,
                            characterEnglishName = characterName.english,
                            mediaTitleEnglish = subjectName.english,
                            mediaTitleJapanese = subjectName.native,
                            mediaTitleRomaji = subjectName.romaji
                        )
                    }
                }.distinctBy { "${it.characterId}:${it.mediaId}" }

                val personName = BangumiNameLocalizer.entityFromInfobox(
                    root.strOrNull("name"),
                    root.strOrNull("nameCN") ?: root.strOrNull("name_cn"),
                    info
                )
                val careers = root.optJSONArray("career")?.let { arr ->
                    (0 until arr.length()).mapNotNull { careerLabel(arr.optString(it)) }
                }.orEmpty()
                KitsugiStaffDetail(
                    id = personId,
                    name = personName.display.ifBlank { "Bangumi #$personId" },
                    nativeName = personName.native,
                    alternativeNames = personName.alternatives,
                    imageUrl = root.optJSONObject("images").image("large", "medium", "grid", "small"),
                    biography = root.strOrNull("summary")?.let { cleanMarkup(it) },
                    occupation = careers.joinToString(", ").takeIf { it.isNotBlank() },
                    birthday = info["生日"]?.firstOrNull(),
                    age = info["年龄"]?.firstOrNull(),
                    gender = info["性别"]?.firstOrNull()?.let { genderText(it) },
                    homeTown = info["出生地"]?.firstOrNull(),
                    characterRoles = characterRoles,
                    mediaWorks = works,
                    romanizedName = personName.romaji,
                    englishName = personName.english
                )
            }
        }

    /**
     * p1 infobox: `[{key, values:[{k?, v}]}]`.
     *
     * `k` **alt alan adıdır**: `别名` üst anahtarının altında `英文名`, `罗马字`, `日文名`,
     * `纯假名` gibi gerçek alanlar bulunur. Eski kod yalnızca `v` değerini okuyup alt anahtarı
     * attığı için İngilizce/romaji ad hiçbir zaman `英文名`/`罗马字` alanından gelmiyordu.
     * Artık alt anahtar da haritaya yazılır; `BangumiNameLocalizer` bu alanları doğrudan görür.
     */
    internal fun parseP1Infobox(array: JSONArray?): Map<String, List<String>> {
        val result = linkedMapOf<String, MutableList<String>>()

        fun add(key: String?, value: String?) {
            val cleanKey = key?.trim().orEmpty()
            val cleanValue = value?.trim().orEmpty()
            if (cleanKey.isEmpty() || cleanValue.isEmpty()) return
            val bucket = result.getOrPut(cleanKey) { mutableListOf() }
            if (!bucket.contains(cleanValue)) bucket += cleanValue
        }

        for (entry in array.objects()) {
            val key = entry.strOrNull("key") ?: continue
            when (val raw = entry.opt("values")) {
                is JSONArray -> {
                    for (index in 0 until raw.length()) {
                        val item = raw.optJSONObject(index)
                        val value = if (item != null) {
                            item.strOrNull("v")
                        } else {
                            raw.optString(index).trim().takeIf { it.isNotEmpty() }
                        } ?: continue
                        add(key, value)
                        val subKey = item?.strOrNull("k")
                        if (subKey != null && !subKey.equals(key, ignoreCase = true)) {
                            add(subKey, value)
                        }
                    }
                }
                is String -> add(key, raw)
            }
        }
        return result
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Metin / tarih / eşleme yardımcıları
    // ═════════════════════════════════════════════════════════════════════════

    internal fun characterRole(type: Int): String = when (type) {
        1 -> "Main"
        2 -> "Supporting"
        else -> "Background"
    }

    /** `CharacterCastType`: 0=CV 1=Dub 2=Actor 3=Çince dublaj 4=Japonca dublaj 5=İngilizce 6=Korece. */
    internal fun castLanguage(relation: Int): String = when (relation) {
        0, 4 -> if (isTurkish()) "Japonca" else "Japanese"
        3 -> if (isTurkish()) "Çince" else "Chinese"
        5 -> if (isTurkish()) "İngilizce" else "English"
        6 -> if (isTurkish()) "Korece" else "Korean"
        2 -> if (isTurkish()) "Oyuncu" else "Actor"
        else -> if (isTurkish()) "Dublaj" else "Dubbing"
    }

    private val relationMap = mapOf(
        "改编" to "Adaptation",
        "前传" to "Prequel",
        "续集" to "Sequel",
        "总集篇" to "Summary",
        "全集" to "Full story",
        "番外篇" to "Side story",
        "角色出演" to "Character",
        "相同世界观" to "Alternative setting",
        "不同世界观" to "Alternative setting",
        "不同演绎" to "Alternative version",
        "衍生" to "Spin-off",
        "主线故事" to "Parent story",
        "联动" to "Other",
        "其他" to "Other"
    )

    internal fun relationLabel(cn: String?, en: String?): String {
        val english = cn?.let { relationMap[it] }
            ?: en?.takeIf { it.isNotBlank() }
            ?: cn
            ?: "Relation"
        return english.toTurkishRelationType()
    }

    /**
     * Bangumi `career` enum'ı önce İngilizce normalleştirilir, ardından arayüz diline
     * çevrilir — İngilizce arayüzde Türkçe etiket sızmasın diye.
     */
    private fun careerLabel(career: String): String? {
        val canonical = when (career.lowercase(Locale.ROOT)) {
            "seiyu" -> "Voice Actor"
            "mangaka" -> "Mangaka"
            "producer" -> "Producer"
            "artist" -> "Artist"
            "illustrator" -> "Illustrator"
            "writer" -> "Writer"
            "actor" -> "Actor"
            "director" -> "Director"
            "composer" -> "Composer"
            "" -> null
            else -> career.replaceFirstChar { it.uppercase() }
        }
        return canonical?.toTurkishStaffRole()
    }

    private fun genderText(raw: String): String = when {
        raw.contains("女") || raw.equals("female", true) -> if (isTurkish()) KitsugiStrings.get(R.string.kadin) else "Female"
        raw.contains("男") || raw.equals("male", true) -> if (isTurkish()) "Erkek" else "Male"
        else -> raw
    }

    internal fun formatOf(platform: String?): String? = when {
        platform.isNullOrBlank() -> null
        platform.equals("TV", true) -> "TV"
        platform.equals("OVA", true) -> "OVA"
        platform.contains("剧场版") -> "Movie"
        platform.equals("WEB", true) -> "ONA"
        platform.contains("电视剧") || platform.contains("電視劇") ||
            platform.contains("连续剧") || platform.contains("連續劇") -> "TV"
        platform.contains("电影") || platform.contains("電影") || platform.contains("映画") -> "Movie"
        platform.contains("漫画") -> "Manga"
        platform.contains("小说") -> "Novel"
        else -> platform
    }

    internal fun weekdayEnglish(cn: String): String? = when {
        cn.contains("一") -> "Mondays"
        cn.contains("二") -> "Tuesdays"
        cn.contains("三") -> "Wednesdays"
        cn.contains("四") -> "Thursdays"
        cn.contains("五") -> "Fridays"
        cn.contains("六") -> "Saturdays"
        cn.contains("日") || cn.contains("天") -> "Sundays"
        else -> null
    }

    internal fun durationText(raw: String, isMovie: Boolean): String? {
        val hms = Regex("(\\d{1,2}):(\\d{2})(?::(\\d{2}))?").find(raw)
        val minutes: Int? = if (hms != null) {
            hms.groupValues[1].toInt() * 60 + hms.groupValues[2].toInt()
        } else {
            val labelled = Regex("(\\d{1,3})\\s*(?:分|min|m)").find(raw)?.groupValues?.get(1)?.toIntOrNull()
            labelled ?: Regex("\\d{1,3}").find(raw)?.value?.toIntOrNull()
        }
        if (minutes == null || minutes <= 0) return null
        return (if (isMovie) "$minutes min" else "$minutes min per ep").toTurkishDuration()
    }

    /** "2008年10月2日" / "2008-10-02" / "2008-10" / "2008" → ISO metin. */
    internal fun isoDate(text: String?): String? {
        if (text.isNullOrBlank()) return null
        Regex("(\\d{4})\\D{1,3}(\\d{1,2})\\D{1,3}(\\d{1,2})").find(text)?.let {
            return "%04d-%02d-%02d".format(Locale.ROOT, it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toInt())
        }
        Regex("(\\d{4})\\D{1,3}(\\d{1,2})").find(text)?.let {
            return "%04d-%02d".format(Locale.ROOT, it.groupValues[1].toInt(), it.groupValues[2].toInt())
        }
        return Regex("(\\d{4})").find(text)?.groupValues?.get(1)
    }

    private fun isoToMillis(iso: String?): Long? {
        if (iso.isNullOrBlank()) return null
        val parts = iso.split('-')
        val year = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val month = parts.getOrNull(1)?.toIntOrNull() ?: 1
        val day = parts.getOrNull(2)?.toIntOrNull() ?: 1
        val calendar = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.clear()
        calendar.set(year, (month - 1).coerceIn(0, 11), day.coerceIn(1, 31))
        return calendar.timeInMillis
    }

    private const val DAY_MS = 24L * 60 * 60 * 1000

    internal fun statusOf(subject: BangumiSubject, startIso: String?, endIso: String?, mediaType: MediaType): String? {
        val now = System.currentTimeMillis()
        val startMs = isoToMillis(startIso)
        if (startMs == null) return if (mediaType == MediaType.Manga) null else "Not yet aired"
        if (startMs > now) return "Not yet aired"
        val endMs = isoToMillis(endIso)
        if (mediaType == MediaType.Manga) {
            return when {
                endMs == null -> null
                endMs <= now -> "Finished"
                else -> "Publishing"
            }
        }
        if (endMs != null) return if (endMs <= now) "Finished Airing" else "Currently Airing"
        val platform = subject.platform.orEmpty()
        if (platform.contains("剧场版") || platform.equals("OVA", true)) return "Finished Airing"
        val episodes = subject.totalEpisodes.takeIf { it > 0 } ?: subject.eps
        return if (episodes > 0) {
            // Haftalık yayın varsayımı + 2 hafta tolerans: tahmini bitiş geçtiyse tamamlandı say.
            if (startMs + (episodes + 2L) * 7L * DAY_MS < now) "Finished Airing" else "Currently Airing"
        } else {
            if (now - startMs > 400L * DAY_MS) "Finished Airing" else "Currently Airing"
        }
    }

    internal fun seasonOf(startIso: String?, mediaType: MediaType): String? {
        if (mediaType == MediaType.Manga) return null
        val parts = startIso?.split('-') ?: return null
        val year = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val month = parts.getOrNull(1)?.toIntOrNull() ?: return null
        val season = when (month) {
            in 1..3 -> "Winter"
            in 4..6 -> "Spring"
            in 7..9 -> "Summer"
            else -> "Fall"
        }
        return "$season $year"
    }

    internal fun isMostlyLatin(text: String): Boolean {
        val letters = text.filter { it.isLetter() }
        if (letters.isEmpty()) return false
        return letters.count { it.code < 0x250 }.toDouble() / letters.length >= 0.9
    }

    /**
     * "A、B / C×D" gibi serbest metni şirket adlarına böler (etiket öneklerini atar).
     *
     * Ayraçlar yalnızca PARANTEZ dışında bölme yapar: `ひぐらしのなく頃に解製作委員会
     * 【フロンティアワークス、ジェネオンエンタテインメント、創通】` gibi değerlerde köşeli
     * parantezin içeriği tek bir isimdir ve ortadan bölünürse ("…委員会【フロンティアワークス")
     * ekranda bozuk, tıklanamaz bir çip görünür. Köşeli parantez grubu ayrı bir üretici olarak
     * DE eklenir — komisyon ve üyeleri böylece iki ayrı çipte okunur.
     */
    internal fun splitCompanies(values: List<String>): List<String> {
        val result = mutableListOf<String>()

        fun add(raw: String) {
            val cleaned = raw
                .substringAfter('：')
                .substringAfter(':')
                .trim()
                .trim('(', ')', '（', '）', ' ', '【', '】', '、', '，', ',')
            if (cleaned.isEmpty()) return
            if (cleaned.length > 60) return
            if (result.none { it.equals(cleaned, ignoreCase = true) }) result += cleaned
        }

        for (value in values) {
            var depth = 0
            val current = StringBuilder()
            val bracketGroups = mutableListOf<String>()
            val bracket = StringBuilder()
            for (ch in value) {
                when (ch) {
                    '【', '「', '『', '（', '(' -> {
                        if (depth == 0) bracket.setLength(0)
                        depth++
                        if (depth > 1) bracket.append(ch)
                    }
                    '】', '」', '』', '）', ')' -> {
                        if (depth > 0) depth--
                        if (depth == 0) {
                            bracketGroups += bracket.toString()
                            bracket.setLength(0)
                        } else {
                            bracket.append(ch)
                        }
                    }
                    else -> {
                        if (depth > 0) bracket.append(ch) else current.append(ch)
                    }
                }
            }
            if (depth > 0 && bracket.isNotEmpty()) bracketGroups += bracket.toString()

            // Parantez dışı metin ayraçlarla bölünür.
            current.toString().split(Regex("[、,，/／×;；\\n]")).forEach { add(it) }
            // "X制作委员会【A、B、C】" → komisyonun kendi adı + üyeleri.
            bracketGroups.forEach { group ->
                group.split(Regex("[、,，/／;；]")).forEach { add(it) }
            }
        }
        return result.take(10)
    }

    /** Kanal adları boşlukla ayrılmış gelebilir ("MBS RKB CBC BS-i/BS-TBS"). */
    internal fun splitNetworks(values: List<String>): List<String> =
        values.flatMap { it.split(Regex("[、,，/／\\s]+")) }
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.length <= 24 }
            .distinct()
            .take(12)

    /** BBCode/HTML kalıntılarını ve Bangumi ifadelerini (`(bgm38)`) temizler. */
    internal fun cleanMarkup(text: String): String =
        text.replace("\r\n", "\n")
            .replace(Regex("\\[/?[a-zA-Z*]+(?:=[^\\]]*)?]"), "")
            .replace(Regex("\\(bgm\\d+\\)"), "")
            .replace(Regex("<[^>]+>"), "")
            .replace(Regex("[ \\t]{2,}"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()

    // ── Stüdyo / kurum kimliği (çiplerin tıklanabilmesi için) ────────────────

    /**
     * Bangumi infobox'ındaki stüdyo/üretici adları düz metindir; kimlik taşımaz. Bu yüzden
     * çipler eskiden hiç tıklanamıyordu. Ad, Bangumi'nin kurum条目'larına (`/v0/search/persons`,
     * `type = 2` 公司) çevrilir ve bulunan kimlik [KitsugiStudio.id] alanına yazılır — çip artık
     * kurumun Bangumi sayfasını (aynı kişi detay şablonu) açar.
     *
     * Çağrı [enrich] içinde yapılır: ön izleme (Bangumi-yerel detay) ağ beklemeden çizilir,
     * kimlikler zenginleştirme turunda eklenir. Eşleşmeyen adlar `0` olarak önbelleğe alınır.
     */
    private suspend fun withStudioPersonIds(detail: KitsugiMediaDetail): KitsugiMediaDetail {
        // Networks ve serializations (yayıncı ağlar + dergiler) da dahil — Bangumi'de
        // şirket (type=2) kaydı olarak tutulur ve aynı kişi/kurum detay şablonuyla açılır.
        val candidates = (detail.studios + detail.producers + detail.networks + detail.serializations)
            .filter { it.id <= 0 && it.source.equals(SOURCE, ignoreCase = true) && it.name.isNotBlank() }
            .map { it.name }
        if (candidates.isEmpty()) return detail
        val resolved = resolveStudioPersonIds(candidates)
        if (resolved.isEmpty()) return detail

        fun apply(studio: KitsugiStudio): KitsugiStudio {
            if (studio.id > 0 || !studio.source.equals(SOURCE, ignoreCase = true)) return studio
            val key = normalizeCompanyKey(studio.name)
            val personId = resolved[key] ?: return studio
            return studio.copy(
                id = personId,
                imageUrl = studio.imageUrl ?: studioPersonImageCache[key]
            )
        }

        return detail.copy(
            studios = detail.studios.map(::apply),
            producers = detail.producers.map(::apply),
            networks = detail.networks.map(::apply),
            serializations = detail.serializations.map(::apply)
        )
    }

    private suspend fun resolveStudioPersonIds(names: List<String>): Map<String, Int> = coroutineScope {
        val result = ConcurrentHashMap<String, Int>()
        val pending = names
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { normalizeCompanyKey(it) }
            .take(STUDIO_LOOKUP_LIMIT)
        withTimeoutOrNull(STUDIO_LOOKUP_BATCH_TIMEOUT_MS) {
            pending.map { name ->
                async(Dispatchers.IO) {
                    studioLookupSemaphore.withPermit {
                        val personId = findStudioPersonId(name)
                        if (personId != null) result[normalizeCompanyKey(name)] = personId
                    }
                }
            }.awaitAll()
        }
        result
    }

    /**
     * Tıklama anında kurum adı → Bangumi kimliği çözümlemesi (çipte kimlik taşınmasa bile).
     * Negatif önbellek aynı oturumda tekrar ağ araması yapmaz.
     */
    suspend fun resolveCompanyPersonId(name: String): Int? = findStudioPersonId(name)

    /**
     * p1 (`next.bgm.tv`) uçlarının boş döndüğü stüdyo/şirket kayıtlarında V0 ucu üzerinden
     * kurum kaydını kişi şemasıyla verir.
     *
     * @return kurum adı okunamazsa `null`; çalışma listesi boş kalabilir.
     */
    suspend fun fetchCompanyFallback(personId: Int): KitsugiStudioDetail? = withContext(Dispatchers.IO) {
        if (personId <= 0) return@withContext null
        val root = runCatching { BangumiApiClient.getPerson(personId) }
            .getOrNull() ?: return@withContext null
        val info = BangumiApiClient.parseInfobox(root.optJSONArray("infobox"))
        val nameCn = root.strOrNull("nameCN")
            ?: root.strOrNull("name_cn")
            ?: info["简体中文名"]?.firstOrNull()
            ?: info["中文名"]?.firstOrNull()
        val localized = BangumiNameLocalizer.entityFromInfobox(
            name = root.strOrNull("name"),
            nameCn = nameCn,
            infobox = info
        )
        val name = localized.display.takeIf { it.isNotBlank() && it != "?" }
            ?: return@withContext null
        val about = root.strOrNull("summary")?.let(::cleanMarkup)
            ?: info["地址"]?.firstOrNull()
            ?: info["网址"]?.firstOrNull()
        val works = runCatching {
            BangumiApiClient.getJsonArrayOrNull("/v0/persons/$personId/subjects")
        }.getOrNull().let(::parseCompanySubjectWorks)
        KitsugiStudioDetail(
            id = personId,
            name = name,
            isMain = true,
            imageUrl = root.optJSONObject("images").image("large", "medium", "grid", "small", "common"),
            about = about,
            mediaWorks = works.distinctBy { it.mediaId }
        )
    }

    /** `/v0/persons/{id}/subjects` → kurumun yer aldığı yapımlar. */
    private fun parseCompanySubjectWorks(array: JSONArray?): List<KitsugiStaffMediaWork> {
        if (array == null) return emptyList()
        val seen = HashSet<Int>()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val subjectId = item.optInt("id", 0).takeIf { it > 0 } ?: return@mapNotNull null
            val subjectType = item.optInt("type", 0)
            if (subjectType != BangumiApiClient.SubjectType.ANIME &&
                subjectType != BangumiApiClient.SubjectType.BOOK &&
                subjectType != BangumiApiClient.SubjectType.REAL
            ) return@mapNotNull null
            val mediaId = BangumiIdNamespace.stableIdFromRaw(subjectId) ?: return@mapNotNull null
            if (!seen.add(subjectId)) return@mapNotNull null
            val title = BangumiNameLocalizer.entity(item.strOrNull("name"), item.strOrNull("name_cn"))
            val staffRole = item.strOrNull("staff")?.toLocalizedStaffRole()
                ?.takeIf { it.isNotBlank() }
                ?: if (isTurkish()) "Stüdyo" else "Studio"
            KitsugiStaffMediaWork(
                mediaId = mediaId,
                mediaTitle = title.display.ifBlank { "#$subjectId" },
                mediaImageUrl = BangumiApiClient.absoluteImageUrl(item.strOrNull("image")),
                mediaType = when (subjectType) {
                    BangumiApiClient.SubjectType.BOOK -> "manga"
                    BangumiApiClient.SubjectType.REAL -> "tv"
                    else -> "anime"
                },
                staffRole = staffRole,
                source = SOURCE,
                titleEnglish = title.english,
                titleJapanese = title.native,
                titleRomaji = title.romaji
            )
        }
    }

    /**
     * Kurum adını Bangumi kişi/şirket条目'ı ile eşler; bulunamazsa `null`.
     *
     * Bangumi infobox'ı aynı şirketi `SHUEISHA` (Latin), `集英社` (CJK) ya da
     * `ジェネオン・ユニバーサル・エンターテイメント(NBCユニバーサル・エンターテイメントジャパン)`
     * gibi farklı biçimlerde verebiliyor. [StudioSourceSupport.orgNameVariants] ile üretilen
     * yazımlar sırayla denenir (sözlük köprüsü Latin adı Bangumi'nin lokal adına çevirir) ve
     * eşleşme tüm ad alanları üzerinden normalize edilerek yapılır. Bu, özellikle kurumun
     * Bangumi'de Latin adla değil CJK adla kayıtlı olduğu durumlarda çipleri kurtarır.
     */
    private suspend fun findStudioPersonId(name: String): Int? {
        val key = normalizeCompanyKey(name)
        if (key.isEmpty()) return null
        if (StudioSourceSupport.isCommitteeName(name)) return null
        studioPersonIdCache[key]?.let { cached -> if (cached > 0) return cached }
        studioPersonMissUntil[key]?.let { until ->
            if (System.currentTimeMillis() < until) return null
        }

        val queries = StudioSourceSupport.orgNameVariants(name)
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.length <= 80 }
            .distinct()
            .take(MAX_ORG_QUERY_VARIANTS)
        if (queries.isEmpty()) return null

        // Aranan adın tüm yazımları eşleşme anahtarı olarak kullanılır: arama "集英社" ile
        // yapıldıysa dönen kayıt "SHUEISHA" adıyla da eşleşsin.
        val wantedKeys = buildSet<String> {
            add(key)
            queries.forEach { query ->
                val norm = normalizeCompanyKey(query)
                if (norm.isNotEmpty()) add(norm)
            }
            val latin = BangumiOrgDictionary.latinNameFor(name)
            if (latin != null) {
                val norm = normalizeCompanyKey(latin)
                if (norm.isNotEmpty()) add(norm)
            }
        }

        val token = tokenOrNull()
        // Ağ/zaman aşımı hatası "bulunamadı" anlamına GELMEZ: önbelleğe alınmaz, bir sonraki
        // tıklamada/yeniden denemede tekrar sorgulanır. (Eskiden hata → boş liste → kalıcı 0
        // kaydı oluşuyor, kurum sayfası oturum boyunca açılmıyordu.)
        var networkFailure = false
        var matched: com.kitsugi.animelist.data.auth.BangumiApiClient.BangumiEntity? = null

        for (query in queries) {
            val candidates = try {
                BangumiApiClient.searchPersons(keyword = query, token = token, limit = STUDIO_SEARCH_LIMIT).data
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                Log.w(TAG, "Kurum araması başarısız ($query): ${e.message}")
                networkFailure = true
                continue
            }

            // Öncelik şirket kayıtlarında (type = 2, 公司); şirket eşleşmezse aynı ada sahip
            // kişi kaydı (type = 1 — ör. yapımcı şahıslar) kabul edilir: detay sayfası zaten
            // kişi şablonunu kullanır ve kişinin yapım listesi anlamlı bir sayfa açar.
            fun matches(entity: com.kitsugi.animelist.data.auth.BangumiApiClient.BangumiEntity): Boolean {
                val entityKeys = listOfNotNull(entity.name, entity.nameCn)
                    .map { normalizeCompanyKey(it) }
                    .filter { it.isNotEmpty() }
                if (entityKeys.any { it in wantedKeys }) return true
                // GEVŞEK EŞLEŞME: infobox yazımı ile Bangumi kaydı arasında takma ad ya da
                // uzun ünlü farkı olabiliyor
                // (`ジェネオン・ユニバーサル・エンターテイメント(NBC…)` ↔ `…エンタテイメント`).
                // Birebir anahtar eşitliği bu durumda yetmiyor ve çip "bulunamadı"ya düşüyordu.
                val queryKey = normalizeCompanyKey(query)
                if (queryKey.length < MIN_FUZZY_ORG_KEY) return false
                return entityKeys.any { k ->
                    k.length >= MIN_FUZZY_ORG_KEY && (k.contains(queryKey) || queryKey.contains(k))
                }
            }

            matched = candidates.filter { it.type == 2 }.firstOrNull(::matches)
                ?: candidates.filter { it.type == 1 }.firstOrNull(::matches)
                ?: candidates.filter { it.type == 3 }.firstOrNull(::matches)
            if (matched != null) break
        }

        matched?.images?.let { images ->
            BangumiApiClient.absoluteImageUrl(images.poster)?.let { imageUrl ->
                studioPersonImageCache[key] = imageUrl
            }
        }
        val id = matched?.id?.takeIf { it > 0 }
        if (id == null) {
            // Yalnızca "gerçekten arandı ve bulunamadı" durumu önbelleğe alınır; aramalardan
            // biri ağ hatasıyla düştüyse bir sonraki tıklama yeniden denesin.
            if (!networkFailure) {
                if (studioPersonMissUntil.size > 256) studioPersonMissUntil.clear()
                studioPersonMissUntil[key] = System.currentTimeMillis() + STUDIO_MISS_TTL_MS
            }
            return null
        }
        studioPersonMissUntil.remove(key)
        if (studioPersonIdCache.size > 256) studioPersonIdCache.clear()
        studioPersonIdCache[key] = id
        // Aynı kurumun diğer yazımları da isabetli sayılsın (çip metni değişse bile).
        wantedKeys.forEach { wanted -> studioPersonIdCache[wanted] = id }
        return id
    }

    /**
     * Bangumi `类型` (meta etiket) listesini gösterime hazırlar: Çince/Japonca değerler korunur
     * (çip arayüz diline çevirir), yinelenen yazımlar atılır.
     */
    internal fun metaGenres(metaTags: List<String>): List<String> =
        metaTags.localizedDistinctTags(64)

    /**
     * İki açıklama adayından gösterilecek olanı seçer.
     *
     * Latin (Çince/Japonca olmayan) metin önceliklidir: otomatik çeviri İngilizce kaynaktan
     * çok daha temiz çıkar. Her iki aday da aynı dil grubundaysa Bangumi açıklaması korunur.
     */
    internal fun pickSynopsis(base: String?, other: String?): String? {
        val native = base?.trim()?.takeIf { it.isNotEmpty() }
        val companion = other?.trim()?.takeIf { it.isNotEmpty() }
        return when {
            native == null -> companion
            companion == null -> native
            PreferenceHelpers.hasCjkCharacters(native) && !PreferenceHelpers.hasCjkCharacters(companion) -> companion
            else -> native
        }
    }

    /** Şirket adı karşılaştırma anahtarı: unvan ekleri ve noktalama yok sayılır. */
    internal fun normalizeCompanyKey(name: String): String {
        var key = name.lowercase(Locale.ROOT)
            .replace(Regex("【[^】]*】"), " ")
            .replace(Regex("""\([^)]*\)"""), " ")
            .filter { it.isLetterOrDigit() }
        listOf(
            "株式会社", "有限会社", "有限公司", "股份有限公司", "合同会社",
            "制作委员会", "製作委员会", "制作委員會", "製作委員會",
            "kabushikigaisha", "kabushiki", "kaisha", "yuugen", "goudou",
            "incorporated", "inc", "limited", "ltd", "corporation", "corp",
            "company", "co", "kaka", "kk"
        ).forEach { suffix ->
            while (key.endsWith(suffix)) key = key.removeSuffix(suffix)
        }
        return key.trim()
    }

    private fun epochDate(seconds: Long): String? {
        if (seconds <= 0L) return null
        return runCatching {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(seconds * 1000L))
        }.getOrNull()
    }
}
