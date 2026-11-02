package com.kitsugi.animelist.data.remote

import android.content.Context
import android.util.Log
import com.kitsugi.animelist.data.auth.BangumiApiClient
import com.kitsugi.animelist.data.auth.BangumiAuthStore
import com.kitsugi.animelist.model.MediaType
import kotlinx.coroutines.CancellationException

/**
 * Bangumi kimlik alanı (ID namespace) tek gerçek kaynağı.
 *
 * Kitsugi'de tüm kaynakların kimlikleri `MediaEntry.malId` alanında **offset'li stableId**
 * olarak saklanır; bu sözleşme `MediaIdentity.keys`, birleşik kütüphane gruplaması ve
 * önbellek anahtarları tarafından beklenir (bkz. [KitsuIdNamespace]):
 *
 *  - MAL       → gerçek MAL ID        (1..99M)
 *  - AniList   → `id + 100_000_000`   (100M..199M)
 *  - Simkl     → `id + 150_000_000`   (150M..249M)  *(SimklImportManager)*
 *  - Kitsu     → `id + 300_000_000`   (300M..399M)
 *  - Shikimori → `id + 400_000_000`   (400M..499M)
 *  - **Bangumi → `id + 500_000_000`   (500M..599M)**
 *
 * Bangumi条目 ID'leri (`subject_id`) 7 haneli sayılara kadar çıkabildiği için 100M'lik
 * pencere yeterlidir; pencere dışına taşan bir ID asla stableId diye yorumlanmaz.
 *
 * Ayrıca Bangumi, **kendi ID uzayını** MAL/AniList'ten bağımsız kullanır: 35658 numaralı
 * Bangumi条目'su ile 35658 numaralı MAL anime'si farklı yapımlardır. Bu yüzden çapraz
 * eşitleme (cross-sync) her zaman [resolveBangumiId] üzerinden gerçek eşleşme aramalıdır.
 */
object BangumiIdNamespace {
    private const val TAG = "BangumiIdNamespace"

    /** Bangumi stableId ofseti — `MediaIdentity.keys` ile birebir aynı olmalı. */
    const val BANGUMI_OFFSET: Int = 500_000_000

    const val STABLE_ID_MIN: Int = BANGUMI_OFFSET + 1
    const val STABLE_ID_MAX: Int = BANGUMI_OFFSET + 99_999_999

    /** Kaynak adı — `MediaEntry.source` ve `JikanSearchResult.source` alanında kullanılır. */
    const val SOURCE = "bangumi"

    fun isStableId(value: Int?): Boolean = value != null && value in STABLE_ID_MIN..STABLE_ID_MAX

    fun stableIdOrNull(value: Int?): Int? = value?.takeIf { isStableId(it) }

    /** Bangumi stableId → gerçek `subject_id`. */
    fun rawIdFromStable(stableId: Int?): Int? =
        stableId?.takeIf { it in STABLE_ID_MIN..STABLE_ID_MAX }?.minus(BANGUMI_OFFSET)

    /** Bangumi stableId veya doğrudan raw subject_id → gerçek `subject_id`. */
    fun rawIdOrNull(id: Int?): Int? =
        rawIdFromStable(id) ?: id?.takeIf { it in 1 until BANGUMI_OFFSET }

    /** Bangumi `subject_id` → stableId. Pencereyi taşırıyorsa null. */
    fun stableIdFromRaw(subjectId: Int?): Int? =
        subjectId?.takeIf { it > 0 && it + BANGUMI_OFFSET <= STABLE_ID_MAX }?.plus(BANGUMI_OFFSET)

    /** `MediaEntry.malId` alanında gerçek bir MAL ID'si mi saklı? */
    fun realMalIdOf(storedId: Int?): Int? = storedId?.takeIf { it in 1..99_999_999 }

    fun isAnimeType(type: MediaType?): Boolean = type == MediaType.Anime

    /** Bangumi条目 türü/platformu → Kitsugi [MediaType]. */
    fun mediaTypeFor(subjectType: Int, platform: String? = null): MediaType = when (subjectType) {
        BangumiApiClient.SubjectType.BOOK -> MediaType.Manga
        BangumiApiClient.SubjectType.ANIME -> if (isMoviePlatform(platform)) MediaType.Movie else MediaType.Anime
        BangumiApiClient.SubjectType.REAL -> if (isMoviePlatform(platform)) MediaType.Movie else MediaType.TvShow
        else -> MediaType.Anime
    }

    /** Bangumi'de animasyon ve gerçek çekim filmleri ayrı tür kodları kullanır. */
    fun isMoviePlatform(platform: String?): Boolean {
        val normalized = platform?.trim()?.lowercase(java.util.Locale.ROOT).orEmpty()
        return listOf("剧场版", "劇場版", "电影", "電影", "映画", "movie", "film", "cinema", "theatrical")
            .any { token -> normalized.contains(token) }
    }

    fun supportsSubjectType(subjectType: Int): Boolean = subjectType in setOf(
        BangumiApiClient.SubjectType.ANIME,
        BangumiApiClient.SubjectType.BOOK,
        BangumiApiClient.SubjectType.REAL
    )

    /** Bangumi subject type alanı → Kitsugi [MediaType]. */
    fun subjectTypeFor(mediaType: MediaType?): Int = when (mediaType) {
        MediaType.Manga -> BangumiApiClient.SubjectType.BOOK
        MediaType.Movie, MediaType.TvShow -> BangumiApiClient.SubjectType.REAL
        else -> BangumiApiClient.SubjectType.ANIME
    }

    /**
     * Aramada kabul edilebilecek Bangumi türleri.
     *
     * Film, Bangumi'de tek bir subject type değildir: Coraline gibi animasyon filmler
     * `ANIME + 剧场版`, gerçek çekim filmler ise `REAL + 电影` olabilir. [subjectTypeFor]
     * geriye uyumluluk için tek/varsayılan türü döndürür; kimlik araması bu listeyi kullanmalı.
     */
    fun subjectTypesFor(mediaType: MediaType?): List<Int> = when (mediaType) {
        MediaType.Movie -> listOf(BangumiApiClient.SubjectType.ANIME, BangumiApiClient.SubjectType.REAL)
        else -> listOf(subjectTypeFor(mediaType))
    }

    /** Adayın yalnız türüne değil, film/dizi ayrımında platformuna da bakar. */
    internal fun isCompatibleWithMediaType(
        mediaType: MediaType?,
        subjectType: Int,
        platform: String?
    ): Boolean {
        if (subjectType !in subjectTypesFor(mediaType)) return false
        if (mediaType != MediaType.Movie) return true
        return when (subjectType) {
            // Anime ana türü film ve TV serilerini birlikte taşır; film yazımında yalnız
            // açıkça film olarak işaretlenmiş条目 kabul edilir (ör. Coraline: 剧场版).
            BangumiApiClient.SubjectType.ANIME -> isMoviePlatform(platform)
            // Eski REAL aramalarında platform bulunmayan sonuçlar da kabul ediliyordu;
            // platform varsa aynı adlı bir TV kaydını filme yazma.
            BangumiApiClient.SubjectType.REAL -> platform.isNullOrBlank() || isMoviePlatform(platform)
            else -> false
        }
    }

    /**
     * Bir kayıttan güvenilir Bangumi `subject_id` çözer.
     *
     * Sıra (güven derecesine göre azalır):
     *  1. Kayıttaki değer zaten Bangumi aralığındaysa doğrudan kullanılır.
     *  2. Yerel MAL→Bangumi eşleme önbelleği (kullanıcı bazlı, ağ gerektirmez).
     *  3. Bangumi arama ile **sıkı** başlık eşleşmesi (tek ve net sonuçta).
     *
     * Hiçbiri tutmazsa null — çağıran taraf "kimlik belirsiz" davranmalı,
     * rastgele bir ID denememelidir (Kitsu'da yaşanan yanlış detay sayfası hatasının tekrarı).
     */
    suspend fun resolveBangumiId(
        context: Context?,
        storedId: Int?,
        title: String? = null,
        expectedYear: Int? = null,
        isAnime: Boolean = true,
        allowNetwork: Boolean = true,
        subjectType: Int? = null,
        mediaType: MediaType? = null
    ): Int? {
        rawIdFromStable(storedId)?.let { return it }
        if (context == null) return null

        val expectedSubjectTypes = when {
            mediaType != null -> subjectTypesFor(mediaType)
            subjectType != null -> listOf(subjectType)
            else -> listOf(
                if (isAnime) BangumiApiClient.SubjectType.ANIME else BangumiApiClient.SubjectType.BOOK
            )
        }

        // 2) Yerel eşleme önbelleği (anime, kitap ve REAL subject'leri ayrı tutulur).
        // Animasyon filmlerde iki olası türü de okumak gerekir; yalnız REAL anahtarına
        // bakmak daha önce öğrenilmiş ANIME + 剧场版 eşleşmesini görünmez kılıyordu.
        val malId = realMalIdOf(storedId)
        if (malId != null) {
            for (expectedType in expectedSubjectTypes) {
                val cached = runCatching { BangumiLocalMappingCache.get(context, malId, expectedType) }
                    .getOrElse { null }
                cached?.let { return it }
            }
        }

        if (!allowNetwork) return null

        // 3) Sıkı başlık araması — NFKC normalize anahtar + infobox takma adları + yıl ayrıştırması
        val query = java.text.Normalizer.normalize(
            title?.trim().orEmpty(),
            java.text.Normalizer.Form.NFKC
        ).trim()
        if (query.isBlank()) return null
        val token = runCatching { BangumiAuthStore.getValidToken(context) }.getOrNull()

        // 3a) Paketlenmiş açık kaynak MAL↔Bangumi eşlemesi yalnız aday üretir.
        // Aday, Bangumi'nin kendi subject kaydındaki alias/tür/yıl ile kanıtlanmadan
        // kullanılmaz; bilinen çelişkili çiftler BangumiMalCrosswalk içinde elenmiştir.
        if (malId != null && BangumiApiClient.SubjectType.ANIME in expectedSubjectTypes) {
            val mappedIds = BangumiMalCrosswalk.bangumiSubjectsForMal(context, malId)
            if (mappedIds.isNotEmpty()) {
                val mappedSubjects = mappedIds.mapNotNull { subjectId ->
                    runCatching { BangumiApiClient.getSubject(subjectId, token) }.getOrElse { error ->
                        if (error is CancellationException) throw error
                        null
                    }
                }
                val mapped = selectUniqueSubjectMatch(
                    subjects = mappedSubjects,
                    query = query,
                    expectedYear = expectedYear,
                    expectedSubjectTypes = expectedSubjectTypes,
                    mediaType = mediaType,
                    allowYearOnlyFallback = false
                )
                if (mapped != null) {
                    runCatching { BangumiLocalMappingCache.put(context, malId, mapped.type, mapped.id) }
                    return mapped.id
                }
            }
        }

        // 3b) Önceki güvenli davranış: sıkı başlık araması.
        val page = runCatching {
            BangumiApiClient.searchSubjects(
                keyword = query,
                token = token,
                types = expectedSubjectTypes,
                limit = 10
            )
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "Bangumi title lookup failed for '$query': ${error.message}")
            return null
        }
        val unique = selectUniqueSubjectMatch(
            subjects = page.data,
            query = query,
            expectedYear = expectedYear,
            expectedSubjectTypes = expectedSubjectTypes,
            mediaType = mediaType
        ) ?: return null

        if (malId != null) {
            // Varsayılan türü değil, sunucunun döndürdüğü gerçek türü öğren. Animasyon
            // filmi ANIME anahtarındayken REAL anahtarına yazmak sonraki aramaları bozar.
            runCatching { BangumiLocalMappingCache.put(context, malId, unique.type, unique.id) }
        }
        return unique.id
    }

    /**
     * Picks the single unambiguous subject match for [query] (`E99`):
     *  - checks `name`, `nameCn`, AND `infobox` alias values,
     *  - when multiple subjects share the same base title in different years (e.g. a manga PV
     *    vs the 2024 TV anime, or 1999 vs 2011 remake), disambiguates by [expectedYear] instead
     *    of failing with `null`.
     */
    internal fun selectUniqueSubjectMatch(
        subjects: List<BangumiApiClient.BangumiSubject>,
        query: String,
        expectedYear: Int?,
        expectedSubjectTypes: List<Int>,
        mediaType: MediaType? = null,
        allowYearOnlyFallback: Boolean = true
    ): BangumiApiClient.BangumiSubject? {
        fun subjectYear(s: BangumiApiClient.BangumiSubject): Int? =
            s.date?.take(4)?.toIntOrNull()?.takeIf { it > 0 }

        fun matchesSubjectAnyName(s: BangumiApiClient.BangumiSubject): Boolean {
            if (matchesTitle(s.name, query) || matchesTitle(s.nameCn, query)) return true
            return s.infobox.values.asSequence().flatten().any { alias -> matchesTitle(alias, query) }
        }

        val typeFiltered = subjects.filter {
            it.type in expectedSubjectTypes &&
                (mediaType == null || isCompatibleWithMediaType(mediaType, it.type, it.platform))
        }
        val exact = typeFiltered.filter(::matchesSubjectAnyName)

        if (exact.size == 1) {
            val single = exact.first()
            val y = subjectYear(single)
            if (expectedYear == null || y == null || kotlin.math.abs(y - expectedYear) <= 1) {
                return single
            }
            return null
        }
        if (exact.size > 1 && expectedYear != null) {
            val exactYearHits = exact.filter { subjectYear(it) == expectedYear }
            if (exactYearHits.size == 1) return exactYearHits.first()
            val nearYearHits = exact.filter {
                val y = subjectYear(it)
                y != null && kotlin.math.abs(y - expectedYear) <= 1
            }
            if (nearYearHits.size == 1) return nearYearHits.first()
            return null
        }

        if (!allowYearOnlyFallback) return null
        val candidates = typeFiltered.filter { subject ->
            expectedYear == null || subjectYear(subject) == expectedYear
        }
        return candidates.singleOrNull()
    }

    /**
     * Arama sonuçları içinden kabul edilecek TEK adayı seçer (saf fonksiyon, birim testine açık).
     *
     * Kabul kuralı: YALNIZCA tek ve net eşleşme — başlık (name/nameCn) normalize edilmiş olarak
     * birebir tutmalı VE bilinen yıl uyuşmalıdır (adayda yıl yoksa adın tek olması yeterli).
     * Eski kod, başlık tutmasa bile yalnızca yıl uyuşan TEK adayı kabul ediyordu
     * (`candidates.size == 1 -> candidates.first()`); bu, aynı yıl çıkan alakasız bir条目'yu
     * yanlış eşleştiriyordu (kullanıcı raporu: "yanlış eşleşme").
     */
    internal fun selectUniqueSubject(
        data: List<BangumiApiClient.BangumiSubject>,
        expectedSubjectType: Int,
        query: String,
        expectedYear: Int?
    ): BangumiApiClient.BangumiSubject? {
        val exact = data.filter { subject ->
            subject.type == expectedSubjectType &&
                (matchesTitle(subject.name, query) || matchesTitle(subject.nameCn, query)) &&
                (expectedYear == null || subject.date?.take(4)?.toIntOrNull() == null ||
                    subject.date?.take(4)?.toIntOrNull() == expectedYear)
        }
        return exact.singleOrNull()
    }

    /** Geriye dönük uyumluluk: tek beklenen subject type alan aşırı yükleme. */
    internal fun selectUniqueSubjectMatch(
        subjects: List<BangumiApiClient.BangumiSubject>,
        query: String,
        expectedYear: Int?,
        expectedSubjectType: Int,
        allowYearOnlyFallback: Boolean = true
    ): BangumiApiClient.BangumiSubject? = selectUniqueSubjectMatch(
        subjects = subjects,
        query = query,
        expectedYear = expectedYear,
        expectedSubjectTypes = listOf(expectedSubjectType),
        mediaType = null,
        allowYearOnlyFallback = allowYearOnlyFallback
    )

    /** Boşluk/noktalama duyarsız başlık karşılaştırması (`MediaIdentity.normalizedTitle` ile aynı ruh). */
    private fun matchesTitle(candidate: String?, query: String): Boolean {
        if (candidate.isNullOrBlank()) return false
        return normalize(candidate) == normalize(query)
    }

    private fun normalize(value: String): String =
        java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFKC)
            .lowercase(java.util.Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]"), "")
}

/**
 * Kullanıcı bazlı, ağ gerektirmeyen MAL ↔ Bangumi eşleme önbelleği.
 *
 * Kitsu'daki `saveKitsuMediaIdForMal` deseniyle aynıdır: bir kez çözülen eşleşme
 * tekrar tekrar arama isteği atmaz. Bangumi tarafında resmî bir MAL eşleme ucu
 * olmadığı için bu önbellek, çapraz eşitleme maliyetini ciddi biçimde düşürür.
 *
 * Kalıcı eşleme kaynağı istenirse `bangumi-data/bangumi-data` (CC BY 4.0) veya
 * `bangumi/Archive` wiki dökümleri `infobox` içindeki MAL/AniList bağlantılarından
 * türetilebilir — bkz. docs/bangumi/BANGUMI_ARASTIRMA_VE_KAYNAKLAR.md.
 */
object BangumiLocalMappingCache {
    private const val PREFS_NAME = "bangumi_id_mapping"

    private fun prefs(context: Context): android.content.SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun subjectTypeKey(subjectType: Int): String = when (subjectType) {
        BangumiApiClient.SubjectType.ANIME -> "a"
        BangumiApiClient.SubjectType.BOOK -> "m"
        BangumiApiClient.SubjectType.REAL -> "r"
        else -> "t$subjectType"
    }

    private fun key(malId: Int, subjectType: Int): String = "bgm_${subjectTypeKey(subjectType)}_$malId"

    private fun legacyKey(malId: Int, isAnime: Boolean) = "bgm_${if (isAnime) "a" else "m"}_$malId"

    fun get(context: Context, malId: Int, subjectType: Int): Int? {
        val preferences = prefs(context)
        val current = preferences.getInt(key(malId, subjectType), 0).takeIf { it > 0 }
        if (current != null) return current
        // Read values written by older app versions that used a boolean anime/book key.
        return when (subjectType) {
            BangumiApiClient.SubjectType.ANIME -> preferences.getInt(legacyKey(malId, true), 0).takeIf { it > 0 }
            BangumiApiClient.SubjectType.BOOK -> preferences.getInt(legacyKey(malId, false), 0).takeIf { it > 0 }
            else -> null
        }
    }

    fun get(context: Context, malId: Int, isAnime: Boolean): Int? = get(
        context,
        malId,
        if (isAnime) BangumiApiClient.SubjectType.ANIME else BangumiApiClient.SubjectType.BOOK
    )

    fun put(context: Context, malId: Int, subjectType: Int, subjectId: Int) {
        if (subjectId <= 0) return
        prefs(context).edit().putInt(key(malId, subjectType), subjectId).apply()
    }

    /**
     * Öğrenilmiş tüm `MAL →条目` eşlemeleri (anahtar biçimi: `bgm_a_<malId>` / `bgm_m_<malId>`).
     * Birleşik kütüphane, Bangumi satırlarını MAL/AniList satırlarıyla eşleştirebilmek için bu
     * haritayı tersine çevirir — bkz. [com.kitsugi.animelist.data.local.MyListIdentityCrosswalk].
     */
    fun snapshot(context: Context): Map<String, Int> = runCatching {
        prefs(context).all.mapNotNull { (prefKey, value) ->
            (value as? Int)?.let { prefKey to it }
        }.toMap()
    }.getOrDefault(emptyMap())

    fun put(context: Context, malId: Int, isAnime: Boolean, subjectId: Int) = put(
        context,
        malId,
        if (isAnime) BangumiApiClient.SubjectType.ANIME else BangumiApiClient.SubjectType.BOOK,
        subjectId
    )

    fun remove(context: Context, malId: Int, isAnime: Boolean) {
        prefs(context).edit().remove(legacyKey(malId, isAnime)).apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
