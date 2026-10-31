package com.kitsugi.animelist.data.auth

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore as CoroutineSemaphore
import kotlinx.coroutines.sync.withLock
import okhttp3.Headers
import java.io.IOException
import java.util.concurrent.Semaphore

/**
 * AniList GraphQL trafiğinin **tek kapısı** (v2.4.341).
 *
 * ## Neden var?
 * 2026-10-31 kod denetiminde AniList'e giden isteklerin **dört ayrı, birbirinden habersiz**
 * yoldan çıktığı görüldü:
 *
 *  1. [com.kitsugi.animelist.data.remote.KitsugiApiBase.executeAniListQuery] → 350 ms aralık
 *     (= dakikada ~171 istek) + 3 eşzamanlı istek. AniList'in **fiili** limiti ise 30/dk.
 *  2. [AniListSyncManager.postAniList] → HİÇ hız sınırı yok. "Listem" ekranındaki
 *     ekleme/düzenleme/silme yazmaları tam olarak bu yoldan gider.
 *  3. [AniListImportManager.postGraphQl] → hız sınırı yok; liste yenileme tek seferde
 *     1 (Viewer) + sayfa sayısı kadar istek fırlatır.
 *  4. Profil ekranlarındaki doğrudan `aniListClient` çağrıları → hız sınırı yok.
 *  Ayrıca [PlatformRateLimiter] yalnızca arka plan senkronunda 2,1 sn aralık uyguluyordu;
 *  diğer üç yol bu bütçeyi tamamen görmezden geliyordu.
 *
 * Sonuç: liste yenileme + birkaç düzenleme + arka plan senkronu bir dakika içinde 30 isteği
 * aşıyor, AniList 429 döndürüyor ve **60 saniyelik** ceza penceresi başlıyor. Kullanıcı
 * tarafında bu "kota doldu" olarak görünüyor; oysa uygulama kendi kotasını tüketiyordu.
 *
 * ## Davranış
 *  - **Kayan pencere**: son 60 sn'de tamamlanan istek sayısı `limit - güvenlik payı`yı aşamaz.
 *    İstek, yanıtı geldiğinde sayılır (AniList de öyle sayar) — böylece bizim penceremiz
 *    AniList'inkinden önce kapanmaz.
 *  - **Kısa patlama sınırı**: 2 sn içinde en fazla [BURST_LIMIT] istek (AniList'in "burst
 *    limiter"ı belgelenmemiş; canlı testlerde 250 ms aralık + küçük patlamalar güvenli).
 *  - **Asgari aralık**: normalde [MIN_GAP_MS], 429 sonrası [MIN_GAP_SLOW_MS].
 *  - **Öncelik**: [Priority.BACKGROUND] istekler pencere bütçesinin yalnızca bir kısmını
 *    kullanabilir; kalanı kullanıcının beklediği işlemler için ayrılır. Böylece arka plan
 *    senkronu listedeki bir düzenlemeyi kotasız bırakmaz.
 *  - **Soğuma**: 429 / `X-RateLimit-Remaining: 0` / gövdede "Too Many Requests" → Retry-After
 *    veya X-RateLimit-Reset kadar (yoksa belgelenen 60 sn) istek gönderilmez. Bekleme
 *    süresi sınırı aşarsa çağırana [QuotaExhaustedException] fırlatılır; **thread kilitlenmez**.
 *  - **Tekilleştirme**: aynı okuma sorgusu uçuştayken ikinci çağrı varsa ikinci HTTP isteği
 *    çıkmaz (ekranların çift tetiklemesi kotayı yemez).
 *  - **Kısa ömürlü okuma önbelleği**: aynı okuma [READ_CACHE_MS] içinde tekrar istenirse
 *    ağa çıkılmaz.
 *
 * Mutasyonlar (ekleme/düzenleme/silme/favori) asla önbelleğe alınmaz ve tekilleştirilmez.
 */
object AniListGate {

    /** İsteğin sahibi: kullanıcı mı bekliyor, arka plan mı çalışıyor, toplu çekim mi? */
    enum class Priority(val maxWaitMs: Long) {
        /** Kullanıcının ekranda beklediği istek: en fazla 8 sn kuyrukta bekler. */
        INTERACTIVE(8_000L),

        /** Arka plan senkronu/bildirim: 25 sn'ye kadar bekleyebilir. */
        BACKGROUND(25_000L),

        /** Liste yenileme/içe aktarma: uzun sürebilir, kullanıcı ilerleme görüyor. */
        BULK(180_000L)
    }

    /** Kota durumu — isteğe bağlı zenginleştirmeleri atlamak için. */
    enum class Budget { OK, CONSERVE, EXHAUSTED }

    /**
     * Kota nedeniyle istek gönderilemedi. [waitMs] kadar sonra denenebilir.
     * Çağıran katman bunu **hata değil, erteleme** olarak ele almalıdır
     * (bkz. `MediaEntryRepository` → bekleyen-senkron kuyruğu).
     */
    class QuotaExhaustedException(
        val waitMs: Long,
        message: String
    ) : IOException(message)

    // ─── Ayar sabitleri ──────────────────────────────────────────────────────
    /** Başlık gelmezse kabul edilen limit (AniList 2026 durumu: 90 dokümante, fiilen 30). */
    private const val FALLBACK_LIMIT = 30

    /** Pencere bütçesinden her zaman boş bırakılan pay (drift/ölçüm hatası payı). */
    private const val SAFETY_MARGIN = 4

    /** Arka plan isteklerine asla verilmeyen, kullanıcıya ayrılmış pay. */
    private const val INTERACTIVE_RESERVE = 6

    /** Toplu çekim (liste yenileme) sırasında kullanıcıya bırakılan pay. */
    private const val BULK_INTERACTIVE_RESERVE = 3

    private const val WINDOW_MS = 60_000L
    private const val BURST_WINDOW_MS = 2_000L
    private const val BURST_LIMIT = 5
    private const val MIN_GAP_MS = 400L
    private const val MIN_GAP_SLOW_MS = 1_500L
    private const val SLOW_MODE_REMAINING_THRESHOLD = 6

    /** 429 başlığı yoksa uygulanan süre: AniList dokümanı "1 dakika timeout" diyor. */
    private const val DEFAULT_COOLDOWN_MS = 60_000L
    private const val MIN_COOLDOWN_MS = 5_000L
    private const val MAX_COOLDOWN_MS = 65_000L

    /** Aynı anda uçuşta olabilecek AniList isteği. */
    private const val MAX_CONCURRENCY = 2

    /** Okuma sorguları için kısa ömürlü yanıt önbelleği. */
    private const val READ_CACHE_MS = 1_500L
    private const val READ_CACHE_MAX_ENTRIES = 64

    private const val POLL_SLICE_MS = 200L

    // ─── Durum ───────────────────────────────────────────────────────────────
    private data class LedgerEntry(val at: Long, val background: Boolean)

    private class CachedRead(val body: String, val expiresAt: Long)

    private val lock = Any()
    /** Bloklayan yol için (java) semafor. */
    private val permits = Semaphore(MAX_CONCURRENCY, true)

    /**
     * Suspend yol için semafor. Java semaforu kullanılsaydı, askıya alınabilir bir
     * fonksiyonda `acquire()` çağıran ana iş parçacığını **bloklayabilirdi**; bu sürüm
     * askıya alır.
     */
    private val suspendPermits = CoroutineSemaphore(MAX_CONCURRENCY)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Son 60 sn'de TAMAMLANAN istekler (AniList sayım kuralı). */
    private val window = ArrayDeque<LedgerEntry>()

    /** Son 2 sn'de GÖNDERİLEN istekler (patlama sınırı). */
    private val burst = ArrayDeque<Long>()

    private var nextSlotAt = 0L
    private var cooldownUntil = 0L
    private var exhaustedUntil = 0L
    private var observedLimit = FALLBACK_LIMIT
    private var slowMode = false

    private val readCache = LinkedHashMap<String, CachedRead>()
    private val inFlight = mutableMapOf<String, CompletableDeferred<Any?>>()
    private val coalesceLock = Mutex()

    /** Testlerde zamanı kontrol edebilmek için (üretimde [System.currentTimeMillis]). */
    @Volatile
    internal var clock: () -> Long = { System.currentTimeMillis() }

    // ───Genel durum ──────────────────────────────────────────────────────────

    /** Kota penceresinde kalan istek hakkı (yaklaşık, negatif olmaz). */
    fun remainingRequests(): Int = synchronized(lock) {
        prune(clock())
        (windowBudget() - window.size).coerceAtLeast(0)
    }

    /** Etkin limit: başlıklardan güncellenir ([FALLBACK_LIMIT] ile başlar). */
    fun observedLimit(): Int = synchronized(lock) { observedLimit }

    /** Soğuma bitene kadar kalan ms (0 → istek gönderilebilir). */
    fun cooldownRemainingMs(): Long = synchronized(lock) {
        val now = clock()
        (maxOf(cooldownUntil, exhaustedUntil) - now).coerceAtLeast(0L)
    }

    fun isCoolingDown(): Boolean = cooldownRemainingMs() > 0L

    /** UI/istemciler için kaba bütçe göstergesi. */
    fun budget(): Budget = synchronized(lock) {
        prune(clock())
        when {
            cooldownRemainingMs() > 0L -> Budget.EXHAUSTED
            remainingRequests() <= 0 -> Budget.EXHAUSTED
            remainingRequests() <= INTERACTIVE_RESERVE -> Budget.CONSERVE
            else -> Budget.OK
        }
    }

    /**
     * Çağıranın, kota nedeniyle ne kadar bekleyeceğini anlatan **kullanıcıya gösterilebilir**
     * metin. [QUOTA_MARKER] içerir; alt katmanlar bu işareti görüp isteği kuyruğa alır.
     */
    fun quotaMessage(waitMs: Long): String {
        val seconds = ((waitMs / 1_000L) + 1L).coerceAtLeast(1L)
        return "$QUOTA_MARKER: $seconds sn sonra otomatik denenecek"
    }

    /** Bir hata metninin kota kaynaklı olup olmadığını anlar (kuyruk/UX ayrımı için). */
    fun isQuotaMessage(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val lower = text.lowercase()
        return text.contains(QUOTA_MARKER) ||
            lower.contains("too many requests") ||
            lower.contains("istek sınırı") ||
            lower.contains("kota") ||
            lower.contains("rate limit") ||
            (lower.contains("429") && (lower.contains("rate") || lower.contains("limit") || lower.contains("hız")))
    }

    /** 429/5xx yanıtını duruma işler. [body] yalnızca teşhis için okunur. */
    fun onResponse(httpCode: Int, headers: Headers?, body: String? = null) {
        val limit = headers?.get("X-RateLimit-Limit")?.toIntOrNull()
        val remaining = headers?.get("X-RateLimit-Remaining")?.toIntOrNull()
        val retryAfterSec = headers?.get("Retry-After")?.trim()?.toLongOrNull()
        val resetSec = headers?.get("X-RateLimit-Reset")?.trim()?.toLongOrNull()
        // Not: başarılı bir gövdede (ör. inceleme metninde) geçen "too many requests"
        // ifadesi kota sanılmamalı → yalnızca başarısız yanıtlarda gövdeye bakılır.
        val bodySaysRateLimited = body != null &&
            httpCode !in 200..299 &&
            body.contains("Too Many Requests", ignoreCase = true)
        val now = clock()

        synchronized(lock) {
            if (limit != null && limit in 1..200) observedLimit = limit

            if (httpCode == 429 || bodySaysRateLimited) {
                slowMode = true
                val waitMs = cooldownFromHeaders(now, retryAfterSec, resetSec)
                cooldownUntil = maxOf(cooldownUntil, now + waitMs)
                return@synchronized
            }

            if (httpCode in 200..299) {
                if (remaining != null && remaining <= 0) {
                    // Kota bitti ama 429 gelmedi: pencere sıfırlanana kadar bekle.
                    slowMode = true
                    exhaustedUntil = maxOf(exhaustedUntil, now + resetFromHeaders(now, resetSec))
                } else if (remaining != null) {
                    slowMode = remaining <= SLOW_MODE_REMAINING_THRESHOLD
                } else {
                    slowMode = false
                }
            }
        }
    }

    // ─── Rezervasyon / bekleme ───────────────────────────────────────────────

    /**
     * Suspend yol: slot alır, [block] çalışır, yanıt sayaca işlenir.
     * Kota nedeniyle [Priority.maxWaitMs] içinde sıra gelmezse [QuotaExhaustedException].
     */
    suspend fun <T> withPermit(
        priority: Priority = Priority.INTERACTIVE,
        maxWaitMs: Long = priority.maxWaitMs,
        block: suspend () -> T
    ): T {
        awaitSlot(priority, maxWaitMs)
        suspendPermits.acquire()
        try {
            return block()
        } finally {
            suspendPermits.release()
            recordCompletion(priority)
        }
    }

    /**
     * Bloklayan yol ([AniListSyncManager] gibi senkron istemciler için).
     * `Thread.sleep` kullanır → **IO/worker thread'inde** çağrılmalıdır.
     */
    fun <T> withPermitBlocking(
        priority: Priority = Priority.INTERACTIVE,
        maxWaitMs: Long = priority.maxWaitMs,
        block: () -> T
    ): T {
        awaitSlotBlocking(priority, maxWaitMs)
        permits.acquire()
        try {
            return block()
        } finally {
            permits.release()
            recordCompletion(priority)
        }
    }

    private suspend fun awaitSlot(priority: Priority, maxWaitMs: Long) {
        val deadline = clock() + maxWaitMs
        while (true) {
            val now = clock()
            val wait = synchronized(lock) { tryReserve(priority, now) }
            if (wait <= 0L) return
            if (now >= deadline) throw QuotaExhaustedException(wait, quotaMessage(wait))
            delay(wait.coerceIn(60L, POLL_SLICE_MS))
        }
    }

    private fun awaitSlotBlocking(priority: Priority, maxWaitMs: Long) {
        val deadline = clock() + maxWaitMs
        while (true) {
            val now = clock()
            val wait = synchronized(lock) { tryReserve(priority, now) }
            if (wait <= 0L) return
            if (now >= deadline) throw QuotaExhaustedException(wait, quotaMessage(wait))
            try {
                Thread.sleep(wait.coerceIn(60L, POLL_SLICE_MS))
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                throw QuotaExhaustedException(wait, quotaMessage(wait))
            }
        }
    }

    /**
     * Sıra varsa rezervasyonu yapar ve `0` döner; yoksa **beklenmesi gereken ms**'yi döner.
     * Rezervasyon yalnızca gerçekten gönderilecek istek için yapılır (patlama sayacına yazılır).
     */
    private fun tryReserve(priority: Priority, now: Long): Long {
        prune(now)

        val cooling = maxOf(cooldownUntil, exhaustedUntil) - now
        if (cooling > 0L) return cooling

        val gap = if (slowMode) MIN_GAP_SLOW_MS else MIN_GAP_MS
        var wait = (nextSlotAt - now).coerceAtLeast(0L)

        // 1) Patlama sınırı
        if (burst.size >= BURST_LIMIT) {
            wait = maxOf(wait, burst.first() + BURST_WINDOW_MS - now)
        }

        // 2) Kayan pencere bütçesi
        if (window.size >= windowBudget()) {
            wait = maxOf(wait, window.first().at + WINDOW_MS - now)
        }

        // 3) Arka plan, kullanıcıya ayrılmış payı yiyemez
        if (priority == Priority.BACKGROUND) {
            val backgroundCount = window.count { it.background }
            if (backgroundCount >= backgroundBudget()) {
                val oldestBackground = window.firstOrNull { it.background }?.at ?: now
                wait = maxOf(wait, oldestBackground + WINDOW_MS - now)
            }
        }

        // 4) Toplu çekim (liste yenileme) da kullanıcıya biraz yer bırakır: 26 isteğin
        // tamamını tek başına tüketirse, yenileme sürerken yapılan bir düzenleme dakikalarca
        // beklemek zorunda kalırdı.
        if (priority == Priority.BULK) {
            val nonInteractiveCount = window.count { it.background }
            if (nonInteractiveCount >= bulkBudget()) {
                val oldest = window.firstOrNull { it.background }?.at ?: now
                wait = maxOf(wait, oldest + WINDOW_MS - now)
            }
        }

        if (wait > 0L) return wait

        nextSlotAt = now + gap
        burst.addLast(now)
        return 0L
    }

    private fun recordCompletion(priority: Priority) = synchronized(lock) {
        val now = clock()
        prune(now)
        // INTERACTIVE dışındaki her istek "kullanıcıya ayrılmış pay" muhasebesine girer.
        window.addLast(LedgerEntry(at = now, background = priority != Priority.INTERACTIVE))
    }

    private fun prune(now: Long) {
        while (window.isNotEmpty() && now - window.first().at >= WINDOW_MS) {
            window.removeFirst()
        }
        while (burst.isNotEmpty() && now - burst.first() >= BURST_WINDOW_MS) {
            burst.removeFirst()
        }
        if (cooldownUntil in 1L..now) cooldownUntil = 0L
        if (exhaustedUntil in 1L..now) exhaustedUntil = 0L
    }

    private fun windowBudget(): Int =
        (observedLimit - SAFETY_MARGIN).coerceIn(BURST_LIMIT, 200)

    private fun backgroundBudget(): Int =
        (windowBudget() - INTERACTIVE_RESERVE).coerceAtLeast(BURST_LIMIT)

    private fun bulkBudget(): Int =
        (windowBudget() - BULK_INTERACTIVE_RESERVE).coerceAtLeast(BURST_LIMIT)

    private fun cooldownFromHeaders(now: Long, retryAfterSec: Long?, resetSec: Long?): Long {
        val fromRetryAfter = retryAfterSec?.takeIf { it > 0L }?.times(1_000L)
        val fromReset = resetSec
            ?.takeIf { it > 0L }
            ?.let { (it * 1_000L - now).takeIf { wait -> wait > 0L } }
        val wait = fromRetryAfter ?: fromReset ?: DEFAULT_COOLDOWN_MS
        return wait.coerceIn(MIN_COOLDOWN_MS, MAX_COOLDOWN_MS)
    }

    private fun resetFromHeaders(now: Long, resetSec: Long?): Long {
        val fromReset = resetSec
            ?.takeIf { it > 0L }
            ?.let { (it * 1_000L - now).takeIf { wait -> wait > 0L } }
        return (fromReset ?: DEFAULT_COOLDOWN_MS).coerceIn(MIN_COOLDOWN_MS, MAX_COOLDOWN_MS)
    }

    // ─── Tekilleştirme + okuma önbelleği ─────────────────────────────────────

    /**
     * Aynı sorgu uçuştayken ikinci çağrıyı aynı yanıta bağlar.
     * [key] `null` ise (mutasyonlar) doğrudan çalışır — mutasyon asla paylaşılmaz.
     */
    suspend fun <T> coalesce(key: String?, block: suspend () -> T): T {
        if (key.isNullOrBlank()) return block()

        val (deferred, isOwner) = coalesceLock.withLock {
            val existing = inFlight[key]
            if (existing != null) {
                existing to false
            } else {
                val created = CompletableDeferred<Any?>()
                inFlight[key] = created
                created to true
            }
        }

        if (!isOwner) {
            @Suppress("UNCHECKED_CAST")
            return deferred.await() as T
        }

        try {
            val value = block()
            deferred.complete(value)
            return value
        } catch (error: Throwable) {
            deferred.completeExceptionally(error)
            throw error
        } finally {
            coalesceLock.withLock { if (inFlight[key] === deferred) inFlight.remove(key) }
        }
    }

    /** Okuma önbelleği anahtarı: token'sız ve token'lı yanıtlar karışmasın. */
    fun cacheKey(query: String, variablesJson: String, token: String?): String {
        val authPart = if (token.isNullOrBlank()) "anon" else "auth${token.hashCode()}"
        return "$authPart|$query|$variablesJson"
    }

    fun cachedRead(key: String): String? = synchronized(lock) {
        val now = clock()
        val entry = readCache[key] ?: return@synchronized null
        if (entry.expiresAt <= now) {
            readCache.remove(key)
            return@synchronized null
        }
        entry.body
    }

    fun putCachedRead(key: String, body: String, ttlMs: Long = READ_CACHE_MS) {
        if (ttlMs <= 0L) return
        synchronized(lock) {
            val now = clock()
            // Süresi geçmişleri temizle, sonra kapasiteyi koru (en eski giriş düşer).
            val iterator = readCache.entries.iterator()
            while (iterator.hasNext()) {
                if (iterator.next().value.expiresAt <= now) iterator.remove()
            }
            readCache[key] = CachedRead(body, now + ttlMs)
            while (readCache.size > READ_CACHE_MAX_ENTRIES) {
                val oldest = readCache.keys.firstOrNull() ?: break
                readCache.remove(oldest)
            }
        }
    }

    /** Bir yazma sonrası (veya çıkışta) okuma önbelleğini geçersiz kılar. */
    fun invalidateReadCache() = synchronized(lock) { readCache.clear() }

    /**
     * Testler için durumu sıfırlar. Üretim kodunda çağrılmamalıdır.
     */
    internal fun resetForTests(now: Long = 0L) {
        synchronized(lock) {
            window.clear()
            burst.clear()
            readCache.clear()
            nextSlotAt = now
            cooldownUntil = 0L
            exhaustedUntil = 0L
            observedLimit = FALLBACK_LIMIT
            slowMode = false
        }
        if (coalesceLock.tryLock()) coalesceLock.unlock()
        inFlight.clear()
    }

    /**
     * Arka planda (ör. soğuma bittiğinde) bir işi çalıştırmak için kullanılan hafif yardımcı.
     * Çağıran zaten kendi scope'una sahipse [kotlinx.coroutines.delay] tercih edilmelidir.
     */
    fun schedule(delayMs: Long, block: suspend () -> Unit) {
        scope.launch {
            if (delayMs > 0L) delay(delayMs)
            runCatching { block() }
        }
    }

    internal companion object {
        /** Kuyruk/UX katmanının kota hatasını tanıdığı işaret. */
        const val QUOTA_MARKER = "AniList istek sınırı (kota) doldu"
    }
}
