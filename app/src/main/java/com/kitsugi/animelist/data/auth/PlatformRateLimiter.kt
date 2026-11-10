package com.kitsugi.animelist.data.auth

import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap

/**
 * Platform bazlı merkezi rate limiter — **AniList hariç**.
 *
 * AniList'in hız/kota yönetimi tamamen [AniListGate] tarafından yapılır; buradaki eski
 * `"anilist" to 2100 ms` kaydı, AniList çağrılarının yalnızca **bir** yolunda (arka plan
 * senkronu) uygulanıyor, yazmalarda ve liste yenilemede hiç uygulanmıyordu. Yani gerçek
 * koruma sağlamıyor, sadece iki ayrı ve tutarsız sayaç üretiyordu (v2.4.341'de kaldırıldı).
 *
 * Diğer platformların belgelenmiş limitleri burada uygulanmaya devam eder:
 * MAL ~150/dk, Kitsu ~60/dk, Shikimori 5/sn, Bangumi ~300/dk, Simkl 1/sn.
 *
 * v2.4.341 düzeltmesi: [notifyRateLimited] artık **mutex tutarken uyumuyor**. Eskiden
 * 429 sonrası 60 sn'lik bekleme kilit altında yapılıyor, bu süre boyunca aynı platformun
 * tüm çağrıları kilitte bekliyordu. Artık yalnızca "bu ana kadar istek yok" işareti
 * yazılır; bekleme bir sonraki [acquire] çağrısında (kilit dışında) uygulanır.
 */
object PlatformRateLimiter {
    private val lastCallTime = ConcurrentHashMap<String, Long>()

    /** 429 alındığında: bu ana kadar yeni istek gönderilmez. */
    private val blockedUntil = ConcurrentHashMap<String, Long>()

    private val guard = Any()

    /**
     * AniList kotası artık TEK merkezde: [AniListGovernor].
     *
     * Eskiden burada 2100 ms'lik ayrı bir sayaç vardı; Keşfet/detay sorguları
     * (KitsugiApiBase) ve liste içe aktarımı bambaşka hızlarda çalıştığı için
     * AniList'in 30 istek/dk sınırı aşılıyordu. Bu platformu yöneticiye devrediyoruz: tek bütçe, tek soğuma.
     */
    private val delegatedPlatforms = setOf("anilist")

    private val platformIntervals = mapOf(
        // AniList bilerek YOK: kota penceresi/patlama/soğuma → AniListGovernor.
        "mal" to 450L,         // ~133 req/dk (Limit ~150/dk)
        "kitsu" to 1100L,      // ~54 req/dk (Limit ~60/dk)
        "shikimori" to 350L,   // ~170 req/dk (Limit 5 req/sn)
        "bangumi" to 400L,     // ~150 req/dk (Limit ~300/dk)
        "simkl" to 1250L       // 0.8 req/sn (Limit 1 req/sn)
        // Jikan burada YOK: kotası JikanGateway'de (3/sn, 55/dk, 429 soğuması, önbellek) yönetilir.
    )

    private fun minIntervalFor(key: String): Long = platformIntervals[key] ?: 300L

    /**
     * Platform için sıra bekler. Kilit yalnızca hesap sırasında tutulur; uyuma (delay)
     * kilit dışında yapılır ki diğer çağrılar birbirini kilitlemesin.
     */
    suspend fun acquire(platform: String) {
        val key = platform.lowercase()
        if (key in delegatedPlatforms) {
            AniListGovernor.acquire()
            return
        }
        while (true) {
            val waitMs = synchronized(guard) {
                val now = System.currentTimeMillis()
                val earliest = maxOf(lastCallTime[key] ?: 0L, blockedUntil[key] ?: 0L)
                var wait = (earliest - now).coerceAtLeast(0L)
                if (wait == 0L) {
                    val last = lastCallTime[key] ?: 0L
                    val elapsed = now - last
                    val interval = minIntervalFor(key)
                    if (elapsed in 0 until interval) wait = interval - elapsed
                }
                if (wait == 0L) lastCallTime[key] = now
                wait
            }
            if (waitMs <= 0L) return
            delay(waitMs.coerceAtMost(1_000L))
        }
    }

    /**
     * 429 yanıtı sonrası platformu [retryAfterSeconds] kadar durdurur (yoksa 3 sn).
     * Çağıranın thread'i **kilit altında** uyutulmaz; yalnızca işaret yazılır ve
     * çağıran kısa bir süre bekletilir (diğer çağrılar da aynı işareti görür).
     */
    suspend fun notifyRateLimited(platform: String, retryAfterSeconds: Long? = null) {
        val key = platform.lowercase()
        if (key in delegatedPlatforms) {
            AniListGovernor.markRateLimited(retryAfterSeconds)
            return
        }
        val waitMs = if (retryAfterSeconds != null && retryAfterSeconds > 0) {
            (retryAfterSeconds * 1_000L).coerceIn(1_000L, 65_000L)
        } else {
            3_000L
        }
        synchronized(guard) {
            val until = System.currentTimeMillis() + waitMs
            blockedUntil[key] = maxOf(blockedUntil[key] ?: 0L, until)
        }
        delay(waitMs.coerceAtMost(5_000L))
    }

    /** Testler/çıkış için: tüm platform işaretlerini temizler. */
    internal fun resetForTests() {
        synchronized(guard) {
            lastCallTime.clear()
            blockedUntil.clear()
        }
    }
}
