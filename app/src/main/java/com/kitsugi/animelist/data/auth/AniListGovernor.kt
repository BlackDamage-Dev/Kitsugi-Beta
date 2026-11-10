package com.kitsugi.animelist.data.auth

import android.util.Log
import kotlinx.coroutines.delay
import okhttp3.Response
import org.json.JSONArray
import kotlin.math.max

/**
 * AniList istekleri için TEK merkezî hız / kesinti yöneticisi.
 *
 * ## Neden var? (kök neden)
 * AniList API'si uzun süredir "degraded state"te ve resmî dokümantasyonunda
 * **90 istek/dk** yazmasına rağmen fiilî limit **30 istek/dk**:
 * > "The API is currently in a degraded state and is limited to 30 requests per minute."
 * > — https://anilist.gitbook.io/anilist-apiv2-docs/docs/guide/rate-limiting
 *
 * Uygulamada bu tek kota ÜÇ ayrı yoldan, BİRBİRİNDEN HABERSİZ tüketiliyordu:
 *  1. `KitsugiApiBase.executeAniListQuery` (Keşfet / detay / bildirim / takvim / profil)
 *     → kendi slot mantığı: normalde 350 ms (≈171 istek/dk!), kısıtlı modda 1500 ms (≈40/dk).
 *       İkisi de 30/dk sınırının ÜSTÜNDE; üstelik 3 eşzamanlı izinle burst üretiyordu.
 *  2. `AniListImportManager.postGraphQl` (Listem → "Senkronize Et" + açılışta otomatik içe aktarım)
 *     → **hiçbir hız sınırlaması yoktu**; 1 Viewer + N sayfa (sayfa başına 50 kayıt)
 *       arka arkaya, hiç beklemeden gönderiliyordu. Tek bir 429 tüm içe aktarımı
 *       çökertiyor, liste hiç yazılamadığı için ekranda sürekli "Listeniz boş" kalıyordu.
 *  3. `AniListSyncManager.postAniList` (durum/puan/bölüm yazma = mutasyon)
 *     → kendi 3 denemelik döngüsü + `PlatformRateLimiter` (ayrı sayaç). 429'da
 *       `PlatformRateLimiter` içinde beklenirken diğer iki yol KOTA YEMEYE DEVAM EDİYORDU.
 *
 * Sonuç: kullanıcı ne yaparsa yapsın "AniList istek sınırı doldu" uyarısı ve boş liste.
 *
 * ## Ne yapıyor?
 *  - **Tek bütçe:** üç yol da aynı slot rezervasyonundan geçer; kotayı yalnızca burada harcarız.
 *  - **Doğru aralık:** `X-RateLimit-Limit` başlığından limiti öğrenir, aralığı `60 sn / limit`
 *    olarak hesaplar (limit gelmediyse 30/dk varsayılır → 2,2 sn). Kota YETMEZSE hızlanmaz.
 *  - **Proaktif bekleme:** `X-RateLimit-Remaining ≤ 1` ise 429 yemeden `X-RateLimit-Reset`
 *    zamanına kadar bekler.
 *  - **429 soğuması:** `Retry-After` / `X-RateLimit-Reset` süresince TÜM yollar 0 ms'de
 *    fail-fast döner; soğuma bitince kaldığı yerden devam eder. Eski kodda soğuma
 *    yalnızca bir yolu durduruyor, diğerleri AniList'i dövmeye devam edip
 *    süreyi uzatıyordu (her 429 cevabı zaman aşımını 1 dk uzatır).
 *  - **GraphQL katmanındaki 429'u da yakalar:** AniList 429'da HTTP koduyla birlikte
 *    gövdeye `{"data":null,"errors":[{"message":"Too Many Requests.","status":429}]}` yazar.
 *    Eski kod bunu "başarılı yanıt" sanıp `data: null` döndürüyordu → sessiz boş sonuçlar.
 *  - **Sunucu şalteri / gateway kesintisi:** "temporarily disabled" mesajı ve ardışık
 *    5xx/timeout için devre kesici (45 sn / 20 sn) — boşuna istek yağdırılmaz.
 *
 * Thread modeli: hem bloklayan (`acquireBlocking`, IO dispatcher'ı için) hem de
 * `suspend` (`acquire`) API sunar; ikisi de aynı atomik slot rezervasyonunu kullanır.
 */
object AniListGovernor {

    private const val TAG = "AniListGovernor"

    // ─── Ayarlar ──────────────────────────────────────────────────────────────
    /** AniList fiilî limiti (degraded state). Başlıktan değer gelmezse bu kullanılır. */
    private const val DEFAULT_LIMIT_PER_MINUTE = 30
    private const val MIN_LIMIT_PER_MINUTE = 10
    private const val MAX_LIMIT_PER_MINUTE = 90
    /** Hesaplanan aralığa eklenen güvenlik payı (saat kayması + burst limiter). */
    private const val SAFETY_MARGIN_MS = 200L
    /** Reset zamanına eklenen pay (AniList penceresi tam kapanmadan istek göndermemek için). */
    private const val RESET_MARGIN_MS = 400L

    private const val SERVICE_DOWN_COOLDOWN_MS = 45_000L
    private const val GATEWAY_DOWN_COOLDOWN_MS = 20_000L
    private const val GATEWAY_FAILURES_BEFORE_BREAK = 2

    /** Bir 429 soğuması için kabul edilen üst sınır (bu süreden uzunsa beklemeyiz). */
    const val MAX_COOLDOWN_MS = 90_000L

    // ─── Durum ────────────────────────────────────────────────────────────────
    private val lock = Any()

    @Volatile private var limitPerMinute: Int = DEFAULT_LIMIT_PER_MINUTE
    @Volatile private var remaining: Int = -1
    @Volatile private var resetEpochMs: Long = 0L
    @Volatile private var nextSlotAtMs: Long = 0L

    @Volatile private var rateLimitedUntilMs: Long = 0L
    @Volatile private var serviceDownUntilMs: Long = 0L
    @Volatile private var serviceDownReason: String =
        "The AniList API has been temporarily disabled due to severe stability issues."
    @Volatile private var gatewayDownUntilMs: Long = 0L

    private var consecutiveGatewayFailures: Int = 0

    // ─── Sonuç tipleri ────────────────────────────────────────────────────────
    sealed class Gate {
        /** İstek gönderilebilir. */
        object Proceed : Gate()
        /** İstek gönderilmemeli: soğuma / kesinti aktif. `retryAfterMs` sonra tekrar denenebilir. */
        class Blocked(val reason: BlockedReason, val retryAfterMs: Long) : Gate()
    }

    enum class BlockedReason { RATE_LIMITED, SERVICE_DOWN, GATEWAY_DOWN }

    sealed class Outcome {
        /** Yanıt kullanılabilir. */
        object Success : Outcome()
        /** 429: soğuma kuruldu, `retryAfterMs` sonra tekrar denenebilir. */
        class RateLimited(val retryAfterMs: Long) : Outcome()
        /** 5xx / ağ hatası: üstel geri çekilme ile denenebilir. */
        class Retryable(val retryAfterMs: Long) : Outcome()
        /** Geçersiz / süresi dolmuş token: yeniden deneme faydasız. */
        object AuthFailure : Outcome()
        /** Kalıcı hata (4xx): yeniden deneme faydasız. */
        object Permanent : Outcome()
        /** AniList sunucu şalteri indi: 45 sn boyunca tüm istekler fail-fast. */
        class ServiceDown(val message: String, val code: Int) : Outcome()
    }

    private sealed class Plan {
        class Go(val waitMs: Long) : Plan()
        class Stop(val reason: BlockedReason, val retryAfterMs: Long) : Plan()
    }

    // ─── Slot planlama ────────────────────────────────────────────────────────
    private fun planLocked(): Plan {
        val now = System.currentTimeMillis()

        if (now < serviceDownUntilMs) return Plan.Stop(BlockedReason.SERVICE_DOWN, serviceDownUntilMs - now)
        if (now < gatewayDownUntilMs) return Plan.Stop(BlockedReason.GATEWAY_DOWN, gatewayDownUntilMs - now)
        if (now < rateLimitedUntilMs) return Plan.Stop(BlockedReason.RATE_LIMITED, rateLimitedUntilMs - now)

        var slot = max(now, nextSlotAtMs)

        // Kota tükenmek üzereyse 429 yemeden pencerenin yenilenmesini bekle.
        if (remaining in 0..1 && resetEpochMs > slot) {
            slot = resetEpochMs + RESET_MARGIN_MS
        }

        val intervalMs = (60_000L / limitPerMinute) + SAFETY_MARGIN_MS
        nextSlotAtMs = slot + intervalMs
        return Plan.Go(slot - now)
    }

    // ─── Çağırma API'si ───────────────────────────────────────────────────────

    /**
     * Bloklayan sürüm — yalnızca `Dispatchers.IO` üzerinde çağrılmalıdır
     * (uygulamadaki tüm AniList yazma yolları zaten IO üzerinde).
     *
     * @param maxWaitMs 429 soğuması için en fazla bu kadar beklenir; daha uzunsa
     *                  [Gate.Blocked] döner (çağıran kullanıcıya "X sn sonra tekrar" diyebilir).
     */
    fun acquireBlocking(maxWaitMs: Long = MAX_COOLDOWN_MS): Gate {
        val deadline = System.currentTimeMillis() + maxWaitMs
        while (true) {
            val plan = synchronized(lock) { planLocked() }
            when (plan) {
                is Plan.Go -> {
                    // maxWaitMs SINIRI BURADA DA GEÇERLİ OLMALI.
                    // Kota tükendiğinde (remaining ≤ 1) plan, sıradaki slotu
                    // `X-RateLimit-Reset` anına kaydırır — bu 60 sn'ye kadar
                    // çıkabilir. Eski kod bu beklemeyi SINIRSIZ yapıyordu:
                    // `maxWaitMs` yalnızca Plan.Stop (429 soğuması) kolunda
                    // kontrol ediliyordu. Sonuç: arka planda bir liste içe
                    // aktarımı varken açılan Keşfet/detay/profil sorguları
                    // onlarca saniye askıda kalıyor, ekran hiç dolmuyordu.
                    if (plan.waitMs > maxWaitMs) {
                        return Gate.Blocked(BlockedReason.RATE_LIMITED, plan.waitMs)
                    }
                    if (plan.waitMs > 0L) sleepQuietly(plan.waitMs)
                    return Gate.Proceed
                }
                is Plan.Stop -> {
                    // Sunucu/gateway kesintisinde beklemenin anlamı yok: fail-fast.
                    if (plan.reason != BlockedReason.RATE_LIMITED) {
                        return Gate.Blocked(plan.reason, plan.retryAfterMs)
                    }
                    if (plan.retryAfterMs > maxWaitMs || System.currentTimeMillis() + plan.retryAfterMs > deadline) {
                        return Gate.Blocked(plan.reason, plan.retryAfterMs)
                    }
                    // Soğumayı bekle: AniList'i dövmeyi bırak, süre bitince devam et.
                    sleepQuietly((plan.retryAfterMs + 100L).coerceAtLeast(100L))
                }
            }
        }
    }

    /** `suspend` sürümü — beklerken iş parçacığını bloke etmez. */
    suspend fun acquire(maxWaitMs: Long = MAX_COOLDOWN_MS): Gate {
        val deadline = System.currentTimeMillis() + maxWaitMs
        while (true) {
            val plan = synchronized(lock) { planLocked() }
            when (plan) {
                is Plan.Go -> {
                    // Yukarıdaki bloklayan sürümdeki gerekçeyle aynı: kota penceresi
                    // yenilenene kadar bekleyen slot, çağıranın `maxWaitMs` sabrını
                    // aşamaz. Aşarsa "şu an meşgul" olarak geri döner; UI yedek
                    // zincire düşer, askıda kalmaz.
                    if (plan.waitMs > maxWaitMs) {
                        return Gate.Blocked(BlockedReason.RATE_LIMITED, plan.waitMs)
                    }
                    if (plan.waitMs > 0L) delay(plan.waitMs)
                    return Gate.Proceed
                }
                is Plan.Stop -> {
                    if (plan.reason != BlockedReason.RATE_LIMITED) {
                        return Gate.Blocked(plan.reason, plan.retryAfterMs)
                    }
                    if (plan.retryAfterMs > maxWaitMs || System.currentTimeMillis() + plan.retryAfterMs > deadline) {
                        return Gate.Blocked(plan.reason, plan.retryAfterMs)
                    }
                    delay((plan.retryAfterMs + 100L).coerceAtLeast(100L))
                }
            }
        }
    }

    // ─── Yanıt gözlemi ────────────────────────────────────────────────────────

    /**
     * HTTP yanıtını işler: hız limiti başlıklarını öğrenir, 429 / 5xx / kesinti
     * durumlarında global soğumayı kurar.
     *
     * DİKKAT: [body] yanıt gövdesinin ÖNCEDEN okunmuş hâli olmalıdır (OkHttp gövdesi
     * tek seferliktir); çağıran gövdeyi kendisi kullanmaya devam eder.
     */
    fun observeResponse(response: Response, body: String?): Outcome {
        // 1) Kota başlıkları — sonraki tüm isteklerin aralığını belirler.
        response.header("X-RateLimit-Limit")?.toIntOrNull()?.let { limit ->
            limitPerMinute = limit.coerceIn(MIN_LIMIT_PER_MINUTE, MAX_LIMIT_PER_MINUTE)
        }
        response.header("X-RateLimit-Remaining")?.toIntOrNull()?.let { remaining = it }
        response.header("X-RateLimit-Reset")?.toLongOrNull()?.let { epochSec ->
            if (epochSec > 0L) resetEpochMs = epochSec * 1_000L
        }

        val code = response.code

        // 2) AniList sunucu şalteri: 200/400/403/503 fark etmez, mesaj varsa kesinti.
        detectServiceDown(body)?.let { message ->
            markServiceDown(message, code)
            return Outcome.ServiceDown(message, code)
        }

        if (code == 429) {
            val cooldownMs = computeCooldownMs(response)
            armRateLimit(cooldownMs)
            return Outcome.RateLimited(cooldownMs)
        }

        if (code in 500..599) {
            noteGatewayFailure()
            val retryAfterMs = response.header("Retry-After")?.toLongOrNull()?.times(1_000L)
                ?.coerceIn(0L, 10_000L) ?: 2_000L
            return Outcome.Retryable(retryAfterMs)
        }

        if (code == 401 || code == 403) {
            return Outcome.AuthFailure
        }

        if (response.isSuccessful) {
            clearGatewayFailures()
            return Outcome.Success
        }

        return Outcome.Permanent
    }

    /**
     * AniList, 429'da HTTP koduyla birlikte gövdeye şunu yazar:
     * `{"data":null,"errors":[{"message":"Too Many Requests.","status":429}]}`
     * Bu katmanı da sınıflandırırız; aksi halde "başarılı" sanılan boş sonuçlar
     * Listem / Keşfet raflarında sessizce "hiçbir şey yok" gibi görünüyordu.
     */
    fun classifyErrors(errors: JSONArray?): Outcome {
        if (errors == null || errors.length() == 0) return Outcome.Success

        var sawAuth = false
        for (i in 0 until errors.length()) {
            val item = errors.optJSONObject(i) ?: continue
            val message = item.optString("message").orEmpty()
            val status = item.optInt("status", 0)

            if (status == 429 ||
                message.contains("Too Many Requests", ignoreCase = true) ||
                message.contains("rate limit", ignoreCase = true)
            ) {
                val cooldownMs = computeCooldownMs(null)
                armRateLimit(cooldownMs)
                return Outcome.RateLimited(cooldownMs)
            }

            if (status == 401 || status == 403 ||
                message.contains("Invalid token", ignoreCase = true) ||
                message.contains("Unauthorized", ignoreCase = true) ||
                message.contains("invalid credentials", ignoreCase = true)
            ) {
                sawAuth = true
            }
        }
        return if (sawAuth) Outcome.AuthFailure else Outcome.Permanent
    }

    /** Taşıma katmanı hatası (timeout, DNS, socket): ardışık olursa devre kesici açılır. */
    fun observeTransportFailure(error: Throwable?) {
        noteGatewayFailure()
        Log.w(TAG, "AniList taşıma hatası: ${error?.message}")
    }

    // ─── Durum bildirimi ──────────────────────────────────────────────────────

    /** Dışarıdan (ör. `PlatformRateLimiter` köprüsü) soğuma bildirmek için. */
    fun markRateLimited(retryAfterSeconds: Long?) {
        val ms = ((retryAfterSeconds ?: 60L) * 1_000L).coerceIn(1_000L, MAX_COOLDOWN_MS)
        armRateLimit(ms)
    }

    fun markServiceDown(reason: String, code: Int = 403): String {
        val cleanReason = reason.trim().ifBlank {
            "The AniList API has been temporarily disabled due to severe stability issues."
        }
        synchronized(lock) {
            serviceDownReason = cleanReason
            serviceDownUntilMs = System.currentTimeMillis() + SERVICE_DOWN_COOLDOWN_MS
        }
        com.kitsugi.animelist.data.platform.PlatformFailureRegistry.report(
            platformLabel = "anilist",
            httpCode = code,
            rawMessage = "AniList API geçici olarak devre dışı: $cleanReason"
        )
        return cleanReason
    }

    fun isRateLimitedNow(): Boolean = System.currentTimeMillis() < rateLimitedUntilMs
    fun rateLimitedUntilEpochMs(): Long = rateLimitedUntilMs
    fun isServiceDownNow(): Boolean = System.currentTimeMillis() < serviceDownUntilMs
    fun serviceDownMessage(): String = serviceDownReason
    fun currentLimitPerMinute(): Int = limitPerMinute

    /** Kullanıcıya gösterilebilir kısa teşhis (geliştirici günlükleri / uyarı bandı). */
    fun debugSnapshot(): String = synchronized(lock) {
        "limit=${limitPerMinute}/dk remaining=$remaining " +
            "reset=${if (resetEpochMs > 0) (resetEpochMs - System.currentTimeMillis()) / 1000 else -1}s " +
            "rateLimited=${((rateLimitedUntilMs - System.currentTimeMillis()) / 1000).coerceAtLeast(0)}s " +
            "serviceDown=${isServiceDownNow()} gatewayDown=${System.currentTimeMillis() < gatewayDownUntilMs}"
    }

    // ─── Yardımcılar ──────────────────────────────────────────────────────────

    /** AniList'in "temporarily disabled" şalter mesajını arar (JSON `errors` veya ham metin). */
    fun detectServiceDown(bodyOrMessage: String?): String? {
        if (bodyOrMessage.isNullOrBlank()) return null
        // Hızlı ön eleme: liste yanıtları MB'lerce olabilir; her yanıtta JSON ayrıştırmak
        // (çağıran zaten ayrıştırıyor) boşuna iş yükü. Anahtar sözcük yoksa hiç bakma.
        if (!bodyOrMessage.contains("disabled", ignoreCase = true) &&
            !bodyOrMessage.contains("stability", ignoreCase = true)
        ) {
            return null
        }
        val jsonMsg = runCatching {
            val parsed = org.json.JSONObject(bodyOrMessage)
            val errors = parsed.optJSONArray("errors")
            errors?.optJSONObject(0)?.optString("message")
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

    private fun computeCooldownMs(response: Response?): Long {
        val now = System.currentTimeMillis()
        val retryAfterSec = response?.header("Retry-After")?.toLongOrNull()
        val resetEpoch = response?.header("X-RateLimit-Reset")?.toLongOrNull()
            ?: (resetEpochMs / 1_000L).takeIf { resetEpochMs > 0L }
        val candidates = listOfNotNull(
            retryAfterSec?.let { it * 1_000L },
            resetEpoch?.let { (it * 1_000L) - now }
        )
        return (candidates.maxOrNull() ?: 60_000L).coerceIn(1_000L, MAX_COOLDOWN_MS)
    }

    private fun armRateLimit(cooldownMs: Long) {
        val until = System.currentTimeMillis() + cooldownMs
        synchronized(lock) {
            // Yeni bir 429, var olan soğumayı kısaltmasın; uzatabilir.
            if (until > rateLimitedUntilMs) rateLimitedUntilMs = until
            remaining = 0
        }
        Log.w(TAG, "AniList 429 — ${cooldownMs / 1000}s soğuma başladı (tüm yollar durdu)")
    }

    private fun noteGatewayFailure() {
        synchronized(lock) {
            consecutiveGatewayFailures++
            if (consecutiveGatewayFailures >= GATEWAY_FAILURES_BEFORE_BREAK) {
                gatewayDownUntilMs = System.currentTimeMillis() + GATEWAY_DOWN_COOLDOWN_MS
                consecutiveGatewayFailures = 0
            }
        }
    }

    private fun clearGatewayFailures() {
        synchronized(lock) { consecutiveGatewayFailures = 0 }
    }

    private fun sleepQuietly(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    /** Testler için durumu sıfırlar. */
    fun resetForTests() {
        synchronized(lock) {
            limitPerMinute = DEFAULT_LIMIT_PER_MINUTE
            remaining = -1
            resetEpochMs = 0L
            nextSlotAtMs = 0L
            rateLimitedUntilMs = 0L
            serviceDownUntilMs = 0L
            serviceDownReason = "The AniList API has been temporarily disabled due to severe stability issues."
            gatewayDownUntilMs = 0L
            consecutiveGatewayFailures = 0
        }
    }
}
