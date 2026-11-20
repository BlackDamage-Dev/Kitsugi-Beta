package com.kitsugi.animelist.data.remote

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.net.URL

/** Jikan uyumlu MAL API'lerinin (miribyou, Tenrai) sonuç tipi. */
sealed class JikanResult {
    class Ok(val body: String) : JikanResult()
    object NotFound : JikanResult()
    class RateLimited(val retryAfterMs: Long) : JikanResult()
    class Failed(val code: Int, val message: String) : JikanResult()
}

/**
 * Jikan-uyumlu MyAnimeList verisi için TEK merkezli istemci.
 *
 * Çağıranlar hâlâ kanonik `https://api.jikan.moe/v4/...` adreslerini kullanır. Gateway bu adresi
 * sırayla şu host'lara çevirip dener (kullanıcı kararı: MAL → miribyou → Tenrai → AniList):
 *
 *  0. **cihaz** — yalnızca `users/...` uçları için: MAL HTML'i uygulama içinde ayrıştırılır
 *     ([com.kitsugi.animelist.data.remote.malweb.MalWebSource]). Her kullanıcının kendi IP'si.
 *
 *  1. **miribyou** — kullanıcının kendi kurduğu, açık kaynak Jikan-uyumlu API. Kök adresi
 *     `miribyou_base_url` (local.properties) → `BuildConfig.MIRIBYOU_BASE_URL`. Boşsa atlanır.
 *  2. **Tenrai** — `https://api.tenrai.org/v1` (Jikan v4 şeması, kimlik doğrulaması yok).
 *
 * Her host'un kendi devre kesicisi ve 429 soğuması vardır; biri çökerse diğeri denenir.
 * AniList yedeği çağıran tarafta (JikanSearchClient / detay istemcileri) kalır.
 *
 * Kota: genel pencere (55 istek/dk UI, 40 arka plan) korunur; host limitleri de bu pencerenin
 * içinde kalır, yani ölçü her zaman muhafazakârdır. 404 kalıcıdır ve bir sonraki host denenir.
 */
object JikanGateway {
    private const val TAG = "JikanGateway"
    private const val CANONICAL_PREFIX = "https://api.jikan.moe/v4/"
    private const val CANONICAL_HOST = "api.jikan.moe"
    private const val TENRAI_BASE = "https://api.tenrai.org/v1"
    private const val TENRAI_MAX_LIMIT = 50
    private const val USER_AGENT = "KitsugiAnimeList/1.0"

    /** Host'lar arası istek aralığı: Jikan/Tenrai 3-4 istek/sn sınırının altında kalmak için. */
    const val MIN_INTERVAL_MS = 350L
    private const val WINDOW_MS = 60_000L
    private const val UI_PER_MINUTE = 55
    private const val BACKGROUND_PER_MINUTE = 40
    private const val DEFAULT_COOLDOWN_MS = 2_000L
    private const val MIN_COOLDOWN_MS = 1_000L
    private const val MAX_COOLDOWN_MS = 10_000L
    private const val DETAIL_TTL_MS = 6L * 60L * 60L * 1000L
    private const val LIST_TTL_MS = 30L * 60L * 1000L
    private const val MAX_CACHE_ENTRIES = 300

    /** Art arda bu kadar sunucu/ağ hatasında o host 5 dk devre dışı kalır. */
    private const val BREAKER_THRESHOLD = 3
    private const val BREAKER_OPEN_MS = 5L * 60L * 1000L
    private const val BREAKER_MSG = "Jikan uyumlu kaynaklar geçici olarak devre dışı (art arda hata)"
    private const val CANCELLED = "cancelled"

    /** Aynı URL için iptal edilmiş sahip sonrası azami yeniden sahiplenme sayısı. */
    private const val MAX_OWNER_ATTEMPTS = 2

    private val ID_DETAIL_REGEX = Regex("^(anime|manga|characters|people|producers)/\\d+(/.*)?$")
    private val VOLATILE_SUFFIXES = listOf("/reviews", "/forum", "/news", "/statistics")

    enum class Priority {
        /** Kullanıcının açık ekranı: tüm kota kullanılabilir. */
        UI,

        /** Arka plan (eşitleme, kimlik doğrulama, profil): pencerenin son 15 hakkına dokunmaz. */
        BACKGROUND
    }

    /** Bir denenecek host: [id] günlük loglar ve host bazlı durum için, [url] gerçek istek adresi. */
    private class Host(val id: String, val url: String)

    private class HostState {
        var consecutiveFailures = 0
        var breakerOpenUntil = 0L
        var cooldownUntil = 0L
    }

    private class CacheEntry(val body: String, val expiresAt: Long)

    private val lock = Any()
    private var nextSlotAt = 0L
    private var globalCooldownUntil = 0L
    private val recentStarts = ArrayDeque<Long>()
    private val hostStates = HashMap<String, HostState>()
    private val inFlight = HashMap<String, CompletableDeferred<JikanResult>>()
    private val cache = object : LinkedHashMap<String, CacheEntry>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>?): Boolean =
            size > MAX_CACHE_ENTRIES
    }

    private val miribyouBase: String
        get() = com.kitsugi.animelist.BuildConfig.MIRIBYOU_BASE_URL.trim().trimEnd('/')

    fun isJikanUrl(url: URL): Boolean = url.host.equals(CANONICAL_HOST, ignoreCase = true)

    /** Uç noktanın önbellek süresi: ID'li ve kararlı detay uçları uzun, liste/arama kısa tutulur. */
    internal fun ttlFor(url: String): Long {
        val path = url.substringAfter("/v4/", "").substringBefore("?")
        if (VOLATILE_SUFFIXES.any { path.endsWith(it) }) return LIST_TTL_MS
        return if (ID_DETAIL_REGEX.matches(path)) DETAIL_TTL_MS else LIST_TTL_MS
    }

    /**
     * Kanonik adresi denenecek host adreslerine çevirir: miribyou (yapılandırılmışsa) → Tenrai.
     * Kanonik olmayan adresler (ör. başka bir domain) değiştirilmeden tek host olarak denenir.
     */
    private fun hostsFor(canonical: String): List<Host> {
        if (!canonical.startsWith(CANONICAL_PREFIX)) return listOf(Host("direct", canonical))
        val rest = canonical.removePrefix(CANONICAL_PREFIX)
        val hosts = ArrayList<Host>(2)
        if (miribyouBase.isNotEmpty() && shouldTryMiribyou(rest)) {
            hosts += Host("miribyou", "$miribyouBase/v4/$rest")
        }
        hosts += Host("tenrai", "$TENRAI_BASE/${tenraiRest(rest)}")
        return hosts
    }

    /**
     * miribyou host'u `top/` uçlarını **404** ile reddediyor (v2.4.491 günlüğünde her ana sayfa
     * açılışında `GET /v4/top/manga → HTTP 404`, 1080 ms). Bu uçlar için önce miribyou'yu denemek
     * her istekte boşa bir tur + kota harcaması demekti. Aynı host bir kez `top/` için 404
     * döndürdüyse, o uçlar doğrudan Tenrai'ye gider; host sonradan başarılı olursa bayrak
     * temizlenir (host güncellenmiş olabilir).
     */
    private fun shouldTryMiribyou(rest: String): Boolean {
        if (!rest.startsWith("top/")) return true
        return synchronized(lock) { !miribyouTopUnsupported }
    }

    @Volatile private var miribyouTopUnsupported = false

    /**
     * Jikan sorgu parametrelerini Tenrai biçimine çevirir: `sfw=true` → `sfw`, `sfw=false` kaldırılır.
     * Tenrai `limit` değerini en fazla 50 kabul eder (`limit=80` → 400 "limit must be <= 50");
     * fazlası bu sınıra indirilir, aksi halde liste uçları (stüdyo yapımları vb.) boş kalır.
     */
    internal fun tenraiRest(rest: String): String {
        val path = rest.substringBefore('?')
        val query = rest.substringAfter('?', "")
        if (query.isEmpty()) return path
        val params = query.split('&').mapNotNull { p ->
            when {
                p == "sfw=true" -> "sfw"
                p == "sfw=false" -> null
                p.startsWith("limit=") -> {
                    val requested = p.removePrefix("limit=").toIntOrNull()
                    if (requested != null && requested > TENRAI_MAX_LIMIT) "limit=$TENRAI_MAX_LIMIT" else p
                }
                else -> p
            }
        }
        return if (params.isEmpty()) path else "$path?${params.joinToString("&")}"
    }

    private fun stateFor(hostId: String): HostState = hostStates.getOrPut(hostId) { HostState() }

    /**
     * Kilit altında bir gönderim slotu rezerve eder ve beklenecek süreyi (ms) döndürür.
     * Genel pencere tüm host'lar için ortaktır (muhafazakâr); host soğumaları ayrıca uygulanır.
     */
    private fun reserveLocked(priority: Priority, hostId: String): Long {
        val now = System.currentTimeMillis()
        while (recentStarts.isNotEmpty() && now - recentStarts.first() >= WINDOW_MS) {
            recentStarts.removeFirst()
        }
        var slot = maxOf(now, nextSlotAt, globalCooldownUntil, stateFor(hostId).cooldownUntil)
        val ceiling = if (priority == Priority.BACKGROUND) BACKGROUND_PER_MINUTE else UI_PER_MINUTE
        if (recentStarts.size >= ceiling) {
            val blocker = recentStarts.elementAt(recentStarts.size - ceiling)
            slot = maxOf(slot, blocker + WINDOW_MS)
        }
        nextSlotAt = slot + MIN_INTERVAL_MS
        recentStarts.addLast(slot)
        return slot - now
    }

    private suspend fun admitHost(priority: Priority, hostId: String) {
        val wait = synchronized(lock) { reserveLocked(priority, hostId) }
        if (wait > 0) delay(wait)
    }

    /** Askıya alınabilen (coroutine) kota kapısı. */
    suspend fun admit(priority: Priority = Priority.UI) = admitHost(priority, "")

    /** Thread bloklayan kota kapısı; yalnızca IO thread'lerinden çağrılmalıdır. */
    fun admitBlocking(priority: Priority = Priority.UI) {
        val wait = synchronized(lock) { reserveLocked(priority, "") }
        if (wait > 0) {
            try {
                Thread.sleep(wait)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    private fun clampCooldown(retryAfterMs: Long?): Long =
        (retryAfterMs ?: DEFAULT_COOLDOWN_MS).coerceIn(MIN_COOLDOWN_MS, MAX_COOLDOWN_MS)

    /** Dışarıdan (ör. profil ekranı) 429 bildirimi: tüm Jikan-uyumlu host'lar için soğuma. */
    fun reportRateLimited(retryAfterMs: Long? = null) {
        val cooldown = clampCooldown(retryAfterMs)
        synchronized(lock) {
            globalCooldownUntil = maxOf(globalCooldownUntil, System.currentTimeMillis() + cooldown)
        }
        Log.w(TAG, "Jikan-uyumlu kaynak 429: ${cooldown}ms soğuma uygulandı")
    }

    private fun reportHostRateLimited(hostId: String, retryAfterMs: Long?) {
        val cooldown = clampCooldown(retryAfterMs)
        synchronized(lock) {
            val st = stateFor(hostId)
            st.cooldownUntil = maxOf(st.cooldownUntil, System.currentTimeMillis() + cooldown)
        }
        Log.w(TAG, "$hostId 429: ${cooldown}ms soğuma")
    }

    private fun isHostBreakerOpen(hostId: String): Boolean = synchronized(lock) {
        stateFor(hostId).breakerOpenUntil > System.currentTimeMillis()
    }

    /** Host'un nihai sonucunu kaydeder; sunucu/ağ hatası eşiği aşınca o host'u [BREAKER_OPEN_MS] kapatır. */
    private fun recordHostOutcome(hostId: String, result: JikanResult) {
        val healthy = result is JikanResult.Ok || result is JikanResult.NotFound
        // v2.4.492: `Job was cancelled` / `Canceled` mesajları da iptaldir. Eskiden bunlar
        // "sunucu hatası" sayılıyordu; kullanıcı ekranı hızlıca değiştirdiğinde 3 iptal
        // host'un devre kesicisini 5 DAKİKA açıyor ve tüm Jikan rafları boş dönüyordu
        // (2026-11-20 günlüğündeki toplu `IOException: Canceled` + boş raf zinciri).
        val serverFailure = result is JikanResult.Failed &&
            !isCancellationMessage(result.message) &&
            (result.code == 0 || result.code in 500..599)
        // miribyou `top/*` uçlarını kalıcı 404 ile reddediyor: bu uçlarda ikinci tur atılmasın.
        if (hostId == "miribyou") {
            if (result is JikanResult.NotFound) miribyouTopUnsupported = true
            else if (healthy) miribyouTopUnsupported = false
        }
        if (!healthy && !serverFailure) return
        synchronized(lock) {
            val st = stateFor(hostId)
            if (healthy) {
                st.consecutiveFailures = 0
            } else {
                st.consecutiveFailures++
                if (st.consecutiveFailures >= BREAKER_THRESHOLD) {
                    st.breakerOpenUntil = System.currentTimeMillis() + BREAKER_OPEN_MS
                    st.consecutiveFailures = 0
                    Log.w(TAG, "$hostId devre açıldı: ${BREAKER_OPEN_MS / 1000}sn boyunca istek atılmayacak")
                }
            }
        }
    }

    /** `Canceled`, `Job was cancelled`, `cancelled` … hepsi iptaldir, hata değildir. */
    private fun isCancellationMessage(message: String?): Boolean {
        val m = message?.lowercase() ?: return false
        return m == CANCELLED || m.contains("cancel")
    }

    private fun cacheGet(key: String): String? = synchronized(lock) {
        val entry = cache[key]
        when {
            entry == null -> null
            entry.expiresAt < System.currentTimeMillis() -> {
                cache.remove(key)
                null
            }
            else -> entry.body
        }
    }

    private fun cachePut(key: String, body: String, ttlMs: Long) {
        synchronized(lock) {
            cache[key] = CacheEntry(body, System.currentTimeMillis() + ttlMs)
        }
    }

    /** Tek bir HTTP denemesi (bloklayan). Kota kapısı çağıran tarafta alınmış olmalıdır. */
    private fun executeOnce(url: String): JikanResult {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .build()
        return try {
            com.kitsugi.animelist.core.network.KitsugiHttpClient.jikanClient.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> JikanResult.Ok(response.body?.string().orEmpty())
                    response.code == 404 -> JikanResult.NotFound
                    response.code == 429 -> JikanResult.RateLimited(
                        response.header("Retry-After")?.toLongOrNull()?.times(1_000L) ?: DEFAULT_COOLDOWN_MS
                    )
                    else -> JikanResult.Failed(response.code, response.message)
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // İptal bir HATA DEĞİLDİR: devre kesiciyi açmamalı, host sağlığını düşürmemeli.
            throw e
        } catch (e: Exception) {
            JikanResult.Failed(0, e.message ?: "network")
        }
    }

    /** Bir host için yeniden denemeli istek. 404 kalıcıdır; 429/5xx/ağ hatası sınırlı sayıda denenir. */
    private suspend fun fetchOnHost(host: Host, priority: Priority, maxRetries: Int): JikanResult {
        var attempt = 0
        var result: JikanResult = JikanResult.Failed(0, CANCELLED)
        while (true) {
            admitHost(priority, host.id)
            result = withContext(Dispatchers.IO) { executeOnce(host.url) }
            when (result) {
                is JikanResult.Ok, is JikanResult.NotFound -> break
                is JikanResult.RateLimited -> {
                    reportHostRateLimited(host.id, result.retryAfterMs)
                    if (attempt >= maxRetries) break
                }
                is JikanResult.Failed -> {
                    val retryable = result.code == 0 || result.code in 500..599
                    if (!retryable || attempt >= maxRetries) break
                    Log.w(TAG, "${host.id} HTTP ${result.code} → yeniden deneme ${attempt + 1}/$maxRetries")
                    delay(1_000L * (1 shl attempt))
                }
            }
            attempt++
        }
        recordHostOutcome(host.id, result)
        return result
    }

    /**
     * Host zincirini sırayla dener (miribyou → Tenrai). İlk başarılı yanıt kazanır ve önbelleğe alınır.
     * Hepsi 404 verirse NotFound, aksi halde son hata döner.
     */
    private suspend fun fetchChain(canonical: String, priority: Priority, maxRetries: Int): JikanResult {
        val ttl = ttlFor(canonical)
        var lastError: JikanResult = JikanResult.Failed(0, BREAKER_MSG)
        var sawNotFound = false

        // 0. Cihaz üzerinde MAL HTML ayrıştırma (kullanıcı uçları). Her kullanıcı kendi IP'sinden
        //    gider; merkezi sunucuya yük binmez. Kendi temposu/önbelleği vardır, Jikan kotasını
        //    tüketmez. Başarısız olursa (ban/captcha/ağ) aşağıdaki miribyou → Tenrai zincirine düşülür.
        if (com.kitsugi.animelist.data.remote.malweb.MalWebSource.supports(canonical) &&
            !isHostBreakerOpen(com.kitsugi.animelist.data.remote.malweb.MalWebSource.HOST_ID)
        ) {
            val deviceId = com.kitsugi.animelist.data.remote.malweb.MalWebSource.HOST_ID
            val r = try {
                com.kitsugi.animelist.data.remote.malweb.MalWebSource.fetch(canonical)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                JikanResult.Failed(0, e.message ?: "device error")
            }
            recordHostOutcome(deviceId, r)
            when (r) {
                is JikanResult.Ok -> {
                    cachePut(canonical, r.body, ttl)
                    return r
                }
                // MAL'in kendisi 404 dediyse kullanıcı gerçekten yoktur; diğer kaynakları yormayız.
                is JikanResult.NotFound -> return r
                else -> {
                    Log.d(TAG, "$deviceId başarısız (${(r as? JikanResult.Failed)?.code}) → sunucu kaynaklarına düşülüyor")
                    lastError = r
                }
            }
        }

        for (host in hostsFor(canonical)) {
            if (isHostBreakerOpen(host.id)) continue
            val r = fetchOnHost(host, priority, maxRetries)
            when (r) {
                is JikanResult.Ok -> {
                    cachePut(canonical, r.body, ttl)
                    return r
                }
                is JikanResult.NotFound -> sawNotFound = true
                else -> lastError = r
            }
        }
        return if (sawNotFound) JikanResult.NotFound else lastError
    }

    /**
     * Jikan-uyumlu GET isteği: önbellek → tekilleştirme → host zinciri (kota + yeniden deneme).
     * [maxRetries] her host için geçerlidir.
     */
    suspend fun fetch(url: String, priority: Priority = Priority.UI, maxRetries: Int = 2): JikanResult {
        cacheGet(url)?.let { return JikanResult.Ok(it) }

        // v2.4.492: eskiden "sahip iptal edildi" durumunda `fetch(...)` kendini SINIRSIZ
        // özyinelemeli çağırıyordu. Kullanıcı hızlıca ekran değiştirdiğinde bu, günlükte
        // görülen ardı ardına `IOException: Canceled` yığınlarını üretiyordu. Şimdi en fazla
        // [MAX_OWNER_ATTEMPTS] kez denenir ve sonuç `Failed` olarak döner (çağıran taraf
        // AniList yedeğine düşer).
        var attempt = 0
        while (true) {
            attempt++
            val owned = CompletableDeferred<JikanResult>()
            val existing = synchronized(lock) {
                inFlight[url] ?: run {
                    inFlight[url] = owned
                    null
                }
            }
            if (existing != null) {
                val shared = existing.await()
                val ownerCancelled = shared is JikanResult.Failed &&
                    shared.code == 0 &&
                    isCancellationMessage(shared.message)
                if (!ownerCancelled || attempt >= MAX_OWNER_ATTEMPTS) return shared
                continue
            }

            var result: JikanResult = JikanResult.Failed(0, CANCELLED)
            try {
                result = fetchChain(url, priority, maxRetries)
            } finally {
                synchronized(lock) { inFlight.remove(url) }
                owned.complete(result)
            }
            return result
        }
    }

    /** Bloklayan sürüm (eski çağıranlar için). IO thread'lerinde kullanın. */
    fun fetchBlocking(url: String, priority: Priority = Priority.UI, maxRetries: Int = 2): JikanResult =
        runBlocking { fetch(url, priority, maxRetries) }
}
