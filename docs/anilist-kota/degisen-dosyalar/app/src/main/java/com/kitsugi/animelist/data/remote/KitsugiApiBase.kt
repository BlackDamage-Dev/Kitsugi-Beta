package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.KitsugiApplication
import com.kitsugi.animelist.data.auth.ExternalAuthManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import com.kitsugi.animelist.utils.cleanShikimoriBbCode

class RateLimitException(message: String) : java.io.IOException(message)
class ResourceNotFoundException(message: String) : java.io.IOException(message)

/**
 * AniList API sunucu tarafında geçici olarak devre dışı bırakıldığında fırlatılır.
 * (HTTP 403 + "temporarily disabled due to severe stability issues" mesajı)
 * Bu exception ExploreViewModel fallback mekanizmasını tetikler.
 */
class AniListServiceDownException(message: String) : Exception(message)

object KitsugiApiBase {
    /**
     * Host bazlı hız bütçesi. Jikan ve Shikimori'nin RESMİ limitleri ayrı ayrı uygulanır:
     *  - Jikan (docs.api.jikan.moe): 3 istek/sn VE 60 istek/dk. Eski 450 ms aralık tek başına
     *    dakikada ~133 istek üretiyordu → 60/dk aşılınca 429 geliyor, karakter/ekip/ilişki
     *    listeleri boş ("bulunamadı") ya da skeleton'da kalıyordu. Artık hem aralık hem de
     *    kayan 60 sn penceresi uygulanır (güvenlik payı ile 55/dk).
     *  - Shikimori: 5 istek/sn sınırı için 450 ms aralık; dakika için güvenli üst sınır (80/dk).
     */
    private class HostBudget(
        val minIntervalMs: Long,
        val perMinute: Int
    ) {
        /** Bir sonraki isteğin başlayabileceği en erken an (ms). */
        var nextSlotAt: Long = 0L
        /** Son 60 sn içinde başlatılan isteklerin zamanları (kayan pencere). */
        val recentStarts = ArrayDeque<Long>()
    }

    private val hostBudgets: Map<String, HostBudget> = mapOf(
        // api.jikan.moe bilerek burada YOK: Jikan kotası JikanGateway tarafından yönetilir
        // (tek kaynak, çift sayım ve çift bekleme olmasın diye).
        // Aralık eskisi gibi (450 ms): Shikimori GraphQL (PlatformRateLimiter) aynı 5 istek/sn
        // havuzunu paylaşıyor; REST aralığını kısaltmak toplamda 429 riskini artırır.
        "shikimori.io" to HostBudget(minIntervalMs = 450L, perMinute = 80),
        "shikimori.one" to HostBudget(minIntervalMs = 450L, perMinute = 80),
        "shikimori.me" to HostBudget(minIntervalMs = 450L, perMinute = 80),
        "shikimori.tv" to HostBudget(minIntervalMs = 450L, perMinute = 80)
    )

    /** Hosttaki (bütçesi olan) bütçeyi döndürür; yoksa null. */
    private fun budgetFor(host: String?): HostBudget? {
        if (host == null) return null
        return hostBudgets[host.lowercase()]
    }

    /**
     * Bu istek için gereken bekleme süresini (ms) hesaplayıp slotu REZERVE eder.
     * Kilit yalnızca hesap için tutulur; bekleme çağıran tarafta yapılır.
     */
    private fun reserveSlotMs(budget: HostBudget): Long = synchronized(budget) {
        val now = System.currentTimeMillis()
        while (budget.recentStarts.isNotEmpty() && now - budget.recentStarts.first() >= 60_000L) {
            budget.recentStarts.removeFirst()
        }
        var slotAt = maxOf(now, budget.nextSlotAt)
        if (budget.recentStarts.size >= budget.perMinute) {
            // Dakika penceresi dolu: en eski isteğin 60 sn'si dolana kadar bekle.
            slotAt = maxOf(slotAt, budget.recentStarts.first() + 60_000L)
        }
        budget.nextSlotAt = slotAt + budget.minIntervalMs
        budget.recentStarts.addLast(slotAt)
        slotAt - now
    }

    /**
     * Jikan/Shikimori isteğini hız bütçesine göre bekletir. Her HTTP denemesi (yeniden
     * denemeler dâhil) tek slot tüketir; bu yüzden tüm GET yolları [performGet] içinde
     * sınırlanır. Blok kendi kendine ek bekleme yapmaz (çift sayım olmasın diye pass-through).
     *
     * ÖNEMLİ: Yalnızca IO thread'lerinde çağrılmalıdır (bekleme `Thread.sleep` ile yapılır).
     */
    suspend fun <T> runWithRateLimit(block: suspend () -> T): T = block()

    /** 429 yanıtında host bütçesinin [delayMs] kadar durmasını sağlar (ortak geri çekilme). */
    private fun penalizeBudget(host: String?, delayMs: Long) {
        val budget = budgetFor(host) ?: return
        synchronized(budget) {
            budget.nextSlotAt = maxOf(budget.nextSlotAt, System.currentTimeMillis() + delayMs)
        }
    }

    /** Hız sınırlı host ise slotu bekler; değilse hemen döner. */
    private fun awaitBudgetSync(host: String?) {
        val budget = budgetFor(host) ?: return
        val waitMs = reserveSlotMs(budget)
        if (waitMs > 0) {
            try {
                Thread.sleep(waitMs)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    // ─── AniList'e özel uyarlanabilir hız sınırlama ve devre kesici ────────────
    // Eskiden tüm uygulama tek bir global Mutex içinde performAniListRequest bitene
    // kadar kilitleniyordu (maxConcurrency = 1 + 700ms + RetryInterceptor çarpanı).
    // Artık slot rezervasyonu ağ G/Ç dışında <1ms'de yapılır, en fazla 3 eşzamanlı
    // istek (Semaphore) çalışır ve X-RateLimit-Limit / X-RateLimit-Remaining başlıklarına
    // göre AniList "degraded mode"a (30 istek/dk) geçtiğinde aralık otomatik büyütülür.
    private const val ANILIST_MIN_INTERVAL_NORMAL_MS = 350L
    private const val ANILIST_MIN_INTERVAL_DEGRADED_MS = 1_500L
    private const val ANILIST_MAX_CONCURRENCY = 3
    private const val ANILIST_MAX_RETRIES = 1
    // 429 sonrası global bekleme üst sınırı: Retry-After değeri ne olursa olsun
    // cooldown en fazla bu kadar uzar; süre dolmadan yeni AniList isteği gönderilmez.
    private const val ANILIST_RATE_LIMIT_COOLDOWN_CAP_MS = 30_000L
    // AniList sunucu şalteri ("temporarily disabled") indirildiğinde 45 sn boyunca
    // kapalı sunucuya boşuna istek yağdırılmaz; tüm çağıranlar 0ms'de fail-fast döner.
    private const val ANILIST_SERVICE_DOWN_COOLDOWN_MS = 45_000L
    // Art arda 2 ağ/gateway (502/503/504/timeout) hatasında kısa devre kesici (20 sn).
    private const val ANILIST_GATEWAY_DOWN_COOLDOWN_MS = 20_000L
    // Bu süreden kısa 429 cooldown'lar fail-fast etmek yerine beklenir.
    private const val ANILIST_SHORT_COOLDOWN_WAIT_MS = 6_000L

    private val aniListSlotLock = Any()
    private val aniListSemaphore = Semaphore(ANILIST_MAX_CONCURRENCY)
    private var nextAniListSlotAtMs: Long = 0L

    @Volatile
    private var aniListDegradedMode: Boolean = false

    @Volatile
    private var anilistRateLimitedUntil: Long = 0L

    @Volatile
    private var anilistServiceDownUntil: Long = 0L

    @Volatile
    private var anilistServiceDownReason: String =
        "The AniList API has been temporarily disabled due to severe stability issues."

    @Volatile
    private var anilistGatewayDownUntil: Long = 0L

    private var consecutiveAniListGatewayFailures: Int = 0

    private fun reserveAniListSlotMs(): Long = synchronized(aniListSlotLock) {
        val now = System.currentTimeMillis()
        val intervalMs = if (aniListDegradedMode) {
            ANILIST_MIN_INTERVAL_DEGRADED_MS
        } else {
            ANILIST_MIN_INTERVAL_NORMAL_MS
        }
        val slotAt = maxOf(now, nextAniListSlotAtMs)
        nextAniListSlotAtMs = slotAt + intervalMs
        slotAt - now
    }

    /** AniList şu anda kapalı, 429 limitinde ya da gateway kesintisinde mi? */
    fun isAniListUnavailableNow(): Boolean {
        val now = System.currentTimeMillis()
        return now < anilistServiceDownUntil ||
            now < anilistGatewayDownUntil ||
            (anilistRateLimitedUntil - now) > ANILIST_SHORT_COOLDOWN_WAIT_MS ||
            AniListQuotaGate.cooldownRemainingMs() > ANILIST_SHORT_COOLDOWN_WAIT_MS
    }

    /**
     * Ham JSON veya hata metni içinde AniList'in sunucu şalteri mesajını arar.
     * AniList bu mesajı bazen HTTP 403, bazen HTTP 503/500, bazen de HTTP 200
     * yanıtının `errors` dizisi içinde döndürür.
     */
    fun extractAniListServiceDownMessage(bodyOrMessage: String?): String? {
        if (bodyOrMessage.isNullOrBlank()) return null
        val jsonMsg = runCatching {
            val errors = Json.parseToJsonElement(bodyOrMessage).jsonObject["errors"]?.jsonArray
            errors?.firstOrNull()?.jsonObject?.get("message")?.jsonPrimitive?.content
        }.getOrNull()
        val candidate = jsonMsg?.takeIf { it.isNotBlank() } ?: bodyOrMessage
        return if (
            candidate.contains("temporarily disabled", ignoreCase = true) ||
            candidate.contains("stability issues", ignoreCase = true)
        ) {
            candidate.trim()
        } else {
            null
        }
    }

    /**
     * AniList servis kesintisini global devre kesiciye ve [com.kitsugi.animelist.data.platform.PlatformFailureRegistry]'ye
     * kaydedip standart [AniListServiceDownException] üretir.
     */
    fun recordAniListServiceDown(
        rawReason: String,
        httpCode: Int? = 403
    ): AniListServiceDownException {
        val cleanReason = rawReason.trim().ifBlank {
            "The AniList API has been temporarily disabled due to severe stability issues."
        }
        anilistServiceDownReason = cleanReason
        anilistServiceDownUntil = System.currentTimeMillis() + ANILIST_SERVICE_DOWN_COOLDOWN_MS
        AniListQuotaGate.onServiceDown(ANILIST_SERVICE_DOWN_COOLDOWN_MS)
        val fullMessage = "AniList API geçici olarak devre dışı: $cleanReason"
        com.kitsugi.animelist.data.platform.PlatformFailureRegistry.report(
            platformLabel = "anilist",
            httpCode = httpCode,
            rawMessage = fullMessage
        )
        return AniListServiceDownException(fullMessage)
    }

    private fun updateAniListRateHeaders(response: okhttp3.Response) {
        val limit = response.header("X-RateLimit-Limit")?.toIntOrNull()
        val remaining = response.header("X-RateLimit-Remaining")?.toIntOrNull()
        val resetEpoch = response.header("X-RateLimit-Reset")?.toLongOrNull()
        // Kapı limiti buradan ÖĞRENİR; 90 varsayımıyla başlamaz.
        AniListQuotaGate.onHttpResponse(limit, remaining, resetEpoch)
        if (limit != null || remaining != null) {
            aniListDegradedMode = (limit != null && limit <= 45) || (remaining != null && remaining <= 12)
        }
    }

    private data class RawGetResult(
        val code: Int,
        val body: String?,
        val retryAfterMs: Long?
    )

    private fun performGet(url: URL): RawGetResult {
        if (JikanGateway.isJikanUrl(url)) return jikanRawGet(url)
        awaitBudgetSync(url.host)
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "KitsugiAnimeList/1.0")
            .build()

        return try {
            com.kitsugi.animelist.core.network.KitsugiHttpClient.metadataClient.newCall(request).execute().use { response ->
                val body = if (response.isSuccessful) response.body?.string() else null
                val retryAfterMs = if (response.code == 429 || response.code in 500..599) {
                    val retryAfterSec = response.header("Retry-After")?.toLongOrNull()
                    retryAfterSec?.let { (it * 1_000L).coerceIn(0L, 10_000L) }
                } else null
                if (response.code == 429) {
                    // Sunucu limiti aştı: aynı host'taki sonraki istekler de kısa süre beklesin.
                    penalizeBudget(url.host, retryAfterMs ?: 2_000L)
                }
                if (body == null) {
                    val safeUrl = SensitiveUrlRedactor.redact(url.toString())
                    if (response.code == 429) {
                        android.util.Log.w("KitsugiApiBase", "HTTP 429 Too Many Requests: Rate limit hit for URL: $safeUrl")
                    } else {
                        android.util.Log.w("KitsugiApiBase", "HTTP Error: ${response.code} ${response.message} for URL: $safeUrl")
                    }
                }
                RawGetResult(response.code, body, retryAfterMs)
            }
        } catch (e: Exception) {
            val safeUrl = SensitiveUrlRedactor.redact(url.toString())
            android.util.Log.e("KitsugiApiBase", "executeGetRequest failed (${e.javaClass.simpleName}) for URL: $safeUrl")
            RawGetResult(0, null, null)
        }
    }

    /**
     * Jikan isteklerini [JikanGateway] üzerinden yapar. Yeniden deneme (429/5xx) çağıran
     * tarafın `executeGetRequestResilient` döngüsünde kalır; kapı kendi içinde ayrıca
     * denemediği için kota çarpanla tüketilmez.
     */
    private fun jikanRawGet(url: URL): RawGetResult =
        when (val r = JikanGateway.fetchBlocking(url.toString(), maxRetries = 0)) {
            is JikanResult.Ok -> RawGetResult(200, r.body, null)
            is JikanResult.NotFound -> RawGetResult(404, null, null)
            is JikanResult.RateLimited -> RawGetResult(429, null, r.retryAfterMs)
            is JikanResult.Failed -> RawGetResult(r.code, null, null)
        }

    fun executeGetRequest(url: URL): String? {
        val result = performGet(url)
        return result.body
    }

    fun executeGetRequestWithHeaders(url: URL, headers: Map<String, String>): String? {
        awaitBudgetSync(url.host)
        val reqBuilder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "KitsugiAnimeList/1.0")
        headers.forEach { (k, v) -> reqBuilder.header(k, v) }
        val request = reqBuilder.build()
        return try {
            com.kitsugi.animelist.core.network.KitsugiHttpClient.metadataClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        } catch (e: Exception) {
            val safeUrl = SensitiveUrlRedactor.redact(url.toString())
            android.util.Log.e("KitsugiApiBase", "executeGetRequestWithHeaders failed (${e.javaClass.simpleName}) for URL: $safeUrl")
            null
        }
    }

    /**
     * 429 (rate-limit) ve 5xx (geçici sunucu hatası) durumlarında kısa bir beklemeyle
     * sınırlı sayıda tekrar deneyen GET.
     *
     * Jikan/Shikimori gibi rate-limit'li kaynaklarda tek seferlik 429 gelirse veri
     * "eksik/boş" dönmek yerine (karakter listesi, ekip, ilişkiler vb.) Retry-After
     * başlığına uyarak (en fazla 5 sn) üstel beklemeyle yeniden çekilir. Kalıcı 4xx
     * hataları (404 vb.) yeniden denenmez.
     *
     * NOT: `Dispatchers.IO` üzerinde çağrılmalıdır (suspend bekleme kullanır).
     */
    suspend fun executeGetRequestResilient(url: URL, maxRetries: Int = 3): String? {
        var attempt = 0
        while (true) {
            val result = performGet(url)
            if (result.body != null) return result.body
            val retryable = result.code == 429 || result.code in 500..599
            if (!retryable || attempt >= maxRetries) return null
            val backoffMs = (result.retryAfterMs ?: (1_000L * (1 shl attempt))).coerceAtMost(5_000L)
            android.util.Log.w(
                "KitsugiApiBase",
                "HTTP ${result.code} → ${backoffMs}ms bekleme ile yeniden deneniyor (deneme ${attempt + 1}/$maxRetries): ${SensitiveUrlRedactor.redact(url.toString())}"
            )
            kotlinx.coroutines.delay(backoffMs)
            attempt++
        }
    }

    fun executeGetRequestOrThrow(url: URL): String {
        if (JikanGateway.isJikanUrl(url)) {
            val safeUrl = SensitiveUrlRedactor.redact(url.toString())
            return when (val r = JikanGateway.fetchBlocking(url.toString(), maxRetries = 0)) {
                is JikanResult.Ok -> r.body
                is JikanResult.NotFound -> throw ResourceNotFoundException("HTTP 404 Not Found for URL: $safeUrl")
                is JikanResult.RateLimited -> throw RateLimitException("HTTP 429 Too Many Requests: Rate limit hit for URL: $safeUrl")
                is JikanResult.Failed -> throw java.io.IOException("HTTP Error ${r.code}: ${r.message} for URL: $safeUrl")
            }
        }
        awaitBudgetSync(url.host)
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "KitsugiAnimeList/1.0")
            .build()

        val safeUrl = SensitiveUrlRedactor.redact(url.toString())
        com.kitsugi.animelist.core.network.KitsugiHttpClient.metadataClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                return response.body?.string().orEmpty()
            } else {
                if (response.code == 429) {
                    penalizeBudget(
                        url.host,
                        response.header("Retry-After")?.toLongOrNull()?.times(1_000L)?.coerceIn(0L, 10_000L) ?: 2_000L
                    )
                    throw RateLimitException("HTTP 429 Too Many Requests: Rate limit hit for URL: $safeUrl")
                } else if (response.code == 404) {
                    throw ResourceNotFoundException("HTTP 404 Not Found for URL: $safeUrl")
                } else {
                    throw java.io.IOException("HTTP Error ${response.code}: ${response.message} for URL: $safeUrl")
                }
            }
        }
    }

    /**
     * AniList GraphQL sorgusu çalıştırır.
     *
     * Hız sınırlama (rate limiting) ve 429 dayanıklılığı BURADA yönetilir:
     *  - [aniListMutex] ile aynı anda tek istek gider (paralel patlamayı önler).
     *  - Her istekten önce [waitForAniListWindow] ile ~700ms bekleme uygulanır.
     *  - 429 (Too Many Requests) veya 5xx gelirse, "Retry-After" başlığına saygı
     *    göstererek üstel bekleme ile [ANILIST_MAX_RETRIES] kez tekrar denenir.
     *
     * NOT: Fonksiyon artık `suspend`. Tüm çağıranlar zaten suspend/withContext(IO)
     * bağlamında olduğu için imza değişikliği çağrı yerlerini bozmaz.
     */
    suspend fun executeAniListQuery(
        query: String,
        variables: JSONObject,
        accessToken: String? = null,
        throwOnServiceDown: Boolean = false,
        priority: AniListQuotaGate.Priority = AniListQuotaGate.Priority.INTERACTIVE
    ): String? {
        val now = System.currentTimeMillis()

        // 1) Sunucu şalteri (temporarily disabled) aktifse ağa hiç çıkma — 0ms fail-fast.
        if (now < anilistServiceDownUntil) {
            val fullMsg = "AniList API geçici olarak devre dışı: $anilistServiceDownReason"
            com.kitsugi.animelist.data.platform.PlatformFailureRegistry.report(
                platformLabel = "anilist",
                httpCode = 403,
                rawMessage = fullMsg
            )
            if (throwOnServiceDown) {
                throw AniListServiceDownException(fullMsg)
            }
            return null
        }

        // 2) Art arda gateway/timeout kesintisi varsa 0ms fail-fast dön.
        if (now < anilistGatewayDownUntil) {
            com.kitsugi.animelist.data.platform.PlatformFailureRegistry.report(
                platformLabel = "anilist",
                httpCode = 503,
                rawMessage = "HTTP 503 · graphql.anilist.co gateway unavailable"
            )
            return null
        }

        // 3) Kota kapısı — tek doğruluk kaynağı.
        //    Kapı; kayan 60 sn penceresini, minimum istek aralığını, 429 penceresini
        //    ve öncelik rezervini birlikte yönetir. Bekleme kapının içinde yapılır,
        //    bu yüzden kısa cooldown'lar burada ayrıca ele alınmaz.
        when (val permit = AniListQuotaGate.acquire(priority)) {
            is AniListQuotaGate.Decision.Granted -> Unit
            is AniListQuotaGate.Decision.Rejected -> {
                android.util.Log.w(
                    "KitsugiApiBase",
                    "AniList kapısı isteği reddetti: ${permit.reason} (${permit.retryInMs}ms sonra)"
                )
                if (permit.reason != AniListQuotaGate.Reason.QUOTA_RESERVED) {
                    com.kitsugi.animelist.data.platform.PlatformFailureRegistry.report(
                        platformLabel = "anilist",
                        httpCode = 429,
                        rawMessage = "HTTP 429 Too Many Requests · graphql.anilist.co"
                    )
                }
                return null
            }
        }

        val requestBody = JSONObject()
            .put("query", query)
            .put("variables", variables)
            .toString()

        val token = if (!accessToken.isNullOrBlank()) {
            accessToken
        } else {
            KitsugiApplication.getInstance()?.let { ctx ->
                ExternalAuthManager.getAniListToken(ctx)
            }
        }

        var attempt = 0
        var tokenFailed = false
        while (true) {
            // Beklerken başka bir istek kesintiyi tespit ettiyse boşuna ağa çıkma
            if (System.currentTimeMillis() < anilistServiceDownUntil) {
                val fullMsg = "AniList API geçici olarak devre dışı: $anilistServiceDownReason"
                if (throwOnServiceDown) throw AniListServiceDownException(fullMsg)
                return null
            }

            val currentToken = if (tokenFailed) null else token
            // Sıra/kota artık AniListQuotaGate'te; burada yalnızca eşzamanlılık sınırlanır.
            val result = aniListSemaphore.withPermit {
                performAniListRequest(requestBody, currentToken)
            }

            when (result) {
                is AniListResult.ServiceDown -> {
                    android.util.Log.e("KitsugiApiBase", "AniList servis kesintisi: ${result.message}")
                    val downEx = recordAniListServiceDown(result.message, result.httpCode)
                    if (throwOnServiceDown) {
                        throw downEx
                    }
                    return null
                }
                is AniListResult.Success -> {
                    // JSON içindeki servis kesintisi veya geçersiz token hatalarını kontrol et
                    extractAniListServiceDownMessage(result.body)?.let { downMsg ->
                        val downEx = recordAniListServiceDown(downMsg, 503)
                        if (throwOnServiceDown) throw downEx
                        return null
                    }
                    val hasAuthError = try {
                        val root = JSONObject(result.body)
                        val errors = root.optJSONArray("errors")
                        if (errors != null && errors.length() > 0) {
                            val msg = errors.optJSONObject(0)?.optString("message").orEmpty().lowercase()
                            msg.contains("invalid token") || msg.contains("unauthorized") || msg.contains("forbidden") || msg.contains("invalid credentials")
                        } else {
                            false
                        }
                    } catch (e: Exception) {
                        false
                    }
                    if (hasAuthError && !currentToken.isNullOrBlank()) {
                        tokenFailed = true
                        continue
                    }
                    return result.body
                }
                is AniListResult.Failure -> {
                    if (!currentToken.isNullOrBlank()) {
                        // Token geçersiz veya yetkisiz, tokensız tekrar dene
                        tokenFailed = true
                        continue
                    }
                    return null   // Kalıcı hata (4xx, retry anlamsız)
                }
                is AniListResult.Retryable -> {
                    if (attempt >= ANILIST_MAX_RETRIES || isAniListUnavailableNow()) return null
                    val backoffMs = (result.retryAfterMs ?: (1_000L * (1 shl attempt))).coerceAtMost(4_000L)
                    delay(backoffMs)
                    attempt++
                }
            }
        }
    }

    // ─── Önbellek + tek-uçuş (single-flight) ────────────────────────────────
    // AniList kotası 30 istek/dk olduğu için "aynı veriyi ikinci kez istemek"
    // lüks değil. Aynı anda iki ekran aynı sorguyu isterse ikincisi birincinin
    // sonucunu bekler; TTL içindeki tekrarlar ağa hiç çıkmaz.

    private data class CachedResponse(val body: String, val storedAtMs: Long)

    private val aniListCache = java.util.concurrent.ConcurrentHashMap<String, CachedResponse>()
    private val aniListKeyLocks = java.util.concurrent.ConcurrentHashMap<String, Mutex>()

    private const val ANILIST_CACHE_MAX_ENTRIES = 250

    /**
     * [executeAniListQuery] + bellek önbelleği + tek-uçuş.
     *
     * @param ttlMs taze kabul edilen süre
     * @param allowStaleMs kota/429 nedeniyle istek gönderilemezse bu yaşa kadar
     *   bayat veri döndürülür (kullanıcı boş ekran yerine eski veriyi görür).
     */
    suspend fun executeAniListQueryCached(
        cacheKey: String,
        ttlMs: Long,
        query: String,
        variables: JSONObject,
        accessToken: String? = null,
        priority: AniListQuotaGate.Priority = AniListQuotaGate.Priority.INTERACTIVE,
        allowStaleMs: Long = 0L
    ): String? {
        val cached = aniListCache[cacheKey]
        if (cached != null && System.currentTimeMillis() - cached.storedAtMs <= ttlMs) {
            return cached.body
        }

        val mutex = aniListKeyLocks.computeIfAbsent(cacheKey) { Mutex() }
        return mutex.withLock {
            val fresh = aniListCache[cacheKey]
            if (fresh != null && System.currentTimeMillis() - fresh.storedAtMs <= ttlMs) {
                return@withLock fresh.body
            }
            val body = executeAniListQuery(
                query = query,
                variables = variables,
                accessToken = accessToken,
                priority = priority
            )
            if (body != null) {
                if (aniListCache.size >= ANILIST_CACHE_MAX_ENTRIES) aniListCache.clear()
                aniListCache[cacheKey] = CachedResponse(body, System.currentTimeMillis())
                body
            } else if (allowStaleMs > 0L && fresh != null &&
                System.currentTimeMillis() - fresh.storedAtMs <= allowStaleMs
            ) {
                android.util.Log.w("KitsugiApiBase", "AniList kotası/kesintisi: bayat önbellek kullanıldı ($cacheKey)")
                fresh.body
            } else {
                null
            }
        }
    }

    /** Kota dolduğunda bile gösterilebilecek son veri var mı? */
    fun peekAniListCache(cacheKey: String): String? = aniListCache[cacheKey]?.body

    /** Mutasyon sonrası ilgili önbellek anahtarlarını düşürür. */
    fun invalidateAniListCache(prefix: String? = null) {
        if (prefix == null) {
            aniListCache.clear()
            return
        }
        aniListCache.keys.filter { it.startsWith(prefix) }.forEach { aniListCache.remove(it) }
    }

    // ─── Kapı köprüleri ─────────────────────────────────────────────────────
    // graphql.anilist.co'ya DOĞRUDAN POST atan eski yollar (AniListSyncManager,
    // AniListImportManager, profil ViewModel'leri, NotificationDiagnostics) bu
    // köprülerden geçmezse kotaları görünmez şekilde tüketir.

    /**
     * Bloklayan çağrı yerleri için. `maxWaitMs` bilinçli olarak KISA: bu yollar
     * ana thread'den çağrılabiliyor; uzun beklemek ANR üretir. Kota yoksa istek
     * hemen reddedilir ve çağıran işi sonraki tura bırakır.
     */
    fun aniListGateBlocking(
        priority: AniListQuotaGate.Priority,
        maxWaitMs: Long = 1_500L
    ): Boolean = AniListQuotaGate.acquireBlocking(priority, maxWaitMs) is
        AniListQuotaGate.Decision.Granted

    suspend fun aniListGate(priority: AniListQuotaGate.Priority): Boolean =
        AniListQuotaGate.acquire(priority) is AniListQuotaGate.Decision.Granted

    /** Teşhis paneli için kota durumu. */
    fun aniListQuotaSnapshot(): AniListQuotaGate.Snapshot = AniListQuotaGate.snapshot()

    private sealed class AniListResult {
        data class Success(val body: String) : AniListResult()
        data object Failure : AniListResult()
        data class Retryable(val retryAfterMs: Long?) : AniListResult()
        /** AniList sunucu tarafında tamamen kapalı — retry anlamsız, fallback gerekli */
        data class ServiceDown(val message: String, val httpCode: Int = 403) : AniListResult()
    }

    private fun performAniListRequest(requestBody: String, token: String?): AniListResult {
        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val body = requestBody.toRequestBody(mediaType)
        val request = Request.Builder()
            .url("https://graphql.anilist.co")
            .post(body)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("User-Agent", "KitsugiAnimeList/1.0")
            .apply {
                if (!token.isNullOrBlank()) {
                    header("Authorization", "Bearer $token")
                }
            }
            .build()

        return try {
            com.kitsugi.animelist.core.network.KitsugiHttpClient.aniListClient.newCall(request).execute().use { response ->
                updateAniListRateHeaders(response)
                if (response.isSuccessful) {
                    synchronized(aniListSlotLock) { consecutiveAniListGatewayFailures = 0 }
                    val responseBody = response.body?.string() ?: ""
                    AniListResult.Success(responseBody)
                } else {
                    val errBody = runCatching { response.body?.string() ?: "" }.getOrDefault("")
                    // 403, 500, 503 fark etmeksizin sunucu şalteri mesajı varsa anında ServiceDown dön
                    extractAniListServiceDownMessage(errBody)?.let { serviceDownMsg ->
                        android.util.Log.w("KitsugiApiBase", "AniList servis kesintisi tespit edildi (${response.code}): $serviceDownMsg")
                        return@use AniListResult.ServiceDown(serviceDownMsg, response.code)
                    }
                    if (response.code == 429 || response.code in 500..599) {
                        android.util.Log.w("KitsugiApiBase", "AniList query retryable failure with code: ${response.code} ${response.message}")
                        val retryAfterSec = response.header("Retry-After")?.toLongOrNull()
                        val resetEpoch = response.header("X-RateLimit-Reset")?.toLongOrNull()
                        val retryAfterMs = when {
                            retryAfterSec != null -> retryAfterSec * 1_000L
                            resetEpoch != null -> (resetEpoch * 1_000L - System.currentTimeMillis()).coerceAtLeast(0L)
                            else -> null
                        }
                        if (response.code == 429) {
                            aniListDegradedMode = true
                            val cooldownMs = (retryAfterMs ?: 5_000L).coerceIn(5_000L, ANILIST_RATE_LIMIT_COOLDOWN_CAP_MS)
                            anilistRateLimitedUntil = System.currentTimeMillis() + cooldownMs
                            // Tüm uygulama için tek 429 penceresi: Retry-After / Reset'e uy.
                            AniListQuotaGate.onRateLimited(retryAfterMs)
                        } else {
                            synchronized(aniListSlotLock) {
                                consecutiveAniListGatewayFailures++
                                if (consecutiveAniListGatewayFailures >= 2) {
                                    anilistGatewayDownUntil = System.currentTimeMillis() + ANILIST_GATEWAY_DOWN_COOLDOWN_MS
                                }
                            }
                        }
                        AniListResult.Retryable(retryAfterMs)
                    } else {
                        android.util.Log.e("KitsugiApiBase", "AniList query permanent failure with code: ${response.code} ${response.message}")
                        AniListResult.Failure
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("KitsugiApiBase", "AniList performRequest Exception: ${e.message}", e)
            com.kitsugi.animelist.data.platform.PlatformFailureRegistry.report("anilist", error = e)
            synchronized(aniListSlotLock) {
                consecutiveAniListGatewayFailures++
                if (consecutiveAniListGatewayFailures >= 2) {
                    anilistGatewayDownUntil = System.currentTimeMillis() + ANILIST_GATEWAY_DOWN_COOLDOWN_MS
                }
            }
            AniListResult.Retryable(null)
        }
    }

    fun classifyNetworkError(error: Throwable): NetworkErrorCategory {
        val message = error.message.orEmpty()

        val isRateLimited = message.contains("429") ||
                message.contains("rate", ignoreCase = true) ||
                message.contains("limit", ignoreCase = true)

        if (isRateLimited) {
            return NetworkErrorCategory.RateLimited
        }

        val isGatewayProblem = message.contains("504") ||
                message.contains("502") ||
                message.contains("503") ||
                message.contains("gateway", ignoreCase = true) ||
                message.contains("timeout", ignoreCase = true)

        if (isGatewayProblem) {
            return NetworkErrorCategory.GatewayProblem
        }

        return NetworkErrorCategory.Other
    }

    enum class NetworkErrorCategory {
        RateLimited,
        GatewayProblem,
        Other
    }
}

// JSON and String helper extensions
fun JSONObject.optNullableString(key: String): String? {
    if (!has(key) || isNull(key)) return null
    val value = optString(key)
    return if (value == "null" || value.isBlank()) null else value
}

fun JSONObject.optionalPositiveInt(key: String): Int? {
    if (!has(key) || isNull(key)) return null
    val value = optInt(key, 0)
    return if (value > 0) value else null
}

fun JSONObject.namesFromObjectArray(key: String): List<String> {
    val array = optJSONArray(key) ?: return emptyList()
    val names = mutableListOf<String>()

    for (index in 0 until array.length()) {
        val item = array.optJSONObject(index) ?: continue
        val name = item.optString("name")
        if (name.isNotBlank() && name != "null") {
            names.add(name)
        }
    }

    return names
}

fun JSONObject.namesFromStringArray(key: String): List<String> {
    val array = optJSONArray(key) ?: return emptyList()
    val names = mutableListOf<String>()

    for (index in 0 until array.length()) {
        val name = array.optString(index)
        if (name.isNotBlank() && name != "null") {
            names.add(name)
        }
    }

    return names
}

fun String.cleanApiText(): String {
    return this
        .cleanShikimoriBbCode()
        .replace("<br>", "\n")
        .replace("<br />", "\n")
        .replace("<br/>", "\n")
        .replace("<i>", "")
        .replace("</i>", "")
        .replace("<b>", "")
        .replace("</b>", "")
        .replace(Regex("<.*?>"), "")
        // HTML entity decode
        .replace("&quot;", "\"")
        .replace("&amp;", "&")
        .replace("&apos;", "'")
        .replace("&#039;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&nbsp;", " ")
        .replace("&ldquo;", "\u201C")
        .replace("&rdquo;", "\u201D")
        .replace("&lsquo;", "\u2018")
        .replace("&rsquo;", "\u2019")
        .replace("&mdash;", "\u2014")
        .replace("&ndash;", "\u2013")
        .replace("&hellip;", "\u2026")
        // Remove excessive blank lines (3+ newlines → 2 newlines)
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}
