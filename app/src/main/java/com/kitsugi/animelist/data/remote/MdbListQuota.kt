package com.kitsugi.animelist.data.remote

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest

/**
 * MDBList hesabının abonelik durumu.
 *
 * **Kota hesabı bu değere göre YAPILMAZ.** Ücretsiz ve ücretli hesapların günlük istek
 * limitleri birbirinden çok farklıdır (1.000 → 1.500.000). Bu yüzden limit her zaman
 * API'nin kendisinden (`X-RateLimit-Limit` başlığı ya da `/user` yanıtındaki
 * `api_requests`) okunur. Bu alan yalnızca arayüzde küçük bir bilgi rozeti çizmek içindir.
 */
enum class MdbListPlan { FREE, SUPPORTER, UNKNOWN }

/**
 * Bir MDBList API anahtarının günlük istek hakkı ve bu hakkın ne kadarının kaldığı.
 *
 * Alanların hepsi `null` olabilir: MDBList bazı yanıtlarda limit/başlık göndermez. Bu durumda
 * arayüz "bilinmiyor" gösterir, uydurma bir sayı göstermez.
 */
data class MdbListQuota(
    val limit: Int? = null,
    val remaining: Int? = null,
    val resetAtEpochSeconds: Long? = null,
    val plan: MdbListPlan = MdbListPlan.UNKNOWN,
    val updatedAtMillis: Long = 0L
) {
    /** Limit biliniyor mu (0 veya negatif limit geçersizdir). */
    val hasLimit: Boolean get() = (limit ?: 0) > 0

    /** Hem limit hem kalan sayısı okunabildi mi? */
    val isKnown: Boolean get() = hasLimit && remaining != null

    /** Günlük hak tükendi mi? (kalan 0 veya altı) */
    val isExhausted: Boolean get() = (remaining ?: Int.MAX_VALUE) <= 0

    /** Kullanılan istek sayısı (bilinmiyorsa null). */
    val used: Int?
        get() = if (limit != null && remaining != null) (limit - remaining).coerceAtLeast(0) else null

    /** Kalan hakkın 0..1 aralığındaki oranı; limit bilinmiyorsa null. */
    val remainingFraction: Float?
        get() {
            val total = limit ?: return null
            val left = remaining ?: return null
            if (total <= 0) return null
            return (left.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        }

    /** Kalan hak azaldı mı (yüzde 10 ve altı)? Uyarı rengi için kullanılır. */
    val isLow: Boolean
        get() = remainingFraction?.let { it <= 0.10f } == true && !isExhausted

    /**
     * Günlük limit 00:00 UTC'de sıfırlanır. Sıfırlanma anı geçtiyse elde kalan eski "0"
     * değeri göstermeye devam etmek yanlış olurdu; bu yüzden kalan hak yeniden limite
     * çekilir ve bir sonraki sıfırlanma anı hesaplanır.
     */
    fun rolledOver(nowMillis: Long = System.currentTimeMillis()): MdbListQuota {
        val resetAt = resetAtEpochSeconds ?: return this
        if (nowMillis < resetAt * 1_000L) return this
        val total = limit ?: return this
        val left = remaining ?: return this
        val nextReset = nextDailyResetEpochSeconds(nowMillis)
        return if (left >= total) {
            copy(resetAtEpochSeconds = nextReset)
        } else {
            copy(remaining = total, resetAtEpochSeconds = nextReset)
        }
    }

    companion object {
        private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1_000L

        /**
         * Verilen ana göre bir sonraki UTC gece yarısının epoch saniyesi.
         *
         * MDBList günlük limiti 00:00 UTC'de sıfırlar. Düz aritmetik kullanılır: epoch
         * milisaniye zaten UTC tabanlıdır ve `Instant.truncatedTo(DAYS)` desteklenmez.
         */
        fun nextDailyResetEpochSeconds(nowMillis: Long = System.currentTimeMillis()): Long =
            ((nowMillis / MILLIS_PER_DAY) + 1L) * (MILLIS_PER_DAY / 1_000L)
    }
}

/**
 * MDBList kotasının süreç içi tek kaynağı.
 *
 * Neden ayrı bir merkez:
 * - Kota **API anahtarına özgüdür**; anahtar değişince eski sayı gösterilmemelidir. Bu yüzden
 *   her kayıt anahtarın SHA-256 parmak iziyle etiketlenir ve yalnızca eşleşen parmak izi
 *   yüklenir.
 * - Aynı değer hem Ayarlar → Harici Entegrasyonlar → MDBList sekmesinde hem de detay
 *   sayfasındaki "Harici Puanlar" kartında gösterilir. İki ekranın ayrı ayrı istek atması
 *   yerine tek bir [StateFlow] paylaşılır.
 * - Her puanlama yanıtı `X-RateLimit-*` başlıklarıyla kotayı zaten günceller; ekstra istek
 *   yalnızca `/user` uç noktasından periyodik doğrulama için atılır (kısa devre + kilit).
 */
object MdbListQuotaCenter {

    private const val TAG = "MdbListQuota"
    private const val PREFS_NAME = "kitsugi_mdb_list_quota"
    private const val KEY_FINGERPRINT = "fingerprint"
    private const val KEY_LIMIT = "limit"
    private const val KEY_REMAINING = "remaining"
    private const val KEY_RESET = "reset_at"
    private const val KEY_PLAN = "plan"
    private const val KEY_UPDATED = "updated_at"

    /** Aynı anda birden fazla ekranın `/user` isteği başlatmasını engeller. */
    private val refreshMutex = Mutex()

    private val _quota = MutableStateFlow<MdbListQuota?>(null)

    /** Arayüzün dinlediği kota. Anahtar yoksa/özellik kapalıysa `null`. */
    val quota: StateFlow<MdbListQuota?> = _quota.asStateFlow()

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var activeFingerprint: String? = null

    @Volatile
    private var lastRefreshAtMillis: Long = 0L

    /** "Kota doldu" uyarısının gösterildiği sıfırlanma anı (tekrar göstermemek için). */
    private val noticeLock = Any()
    private var lastNotifiedResetAt: Long? = null

    /**
     * Gösterilecek anahtarı bağlar. Anahtar boşsa kota temizlenir (özellik kapalıyken
     * detay sayfasında gereksiz uyarı çizilmesini engeller). Aynı anahtar tekrar
     * bağlanırsa disk okuması yapılmaz.
     */
    fun bind(context: Context?, apiKey: String) {
        if (context != null) appContext = context.applicationContext
        val fingerprint = fingerprintOf(apiKey)
        if (fingerprint == activeFingerprint) return
        activeFingerprint = fingerprint
        lastRefreshAtMillis = 0L
        synchronized(noticeLock) { lastNotifiedResetAt = null }
        _quota.value = fingerprint?.let { loadPersisted(it) }
    }

    /**
     * Kotayı `/user` uç noktasından tazeler.
     *
     * @param maxAgeMillis son tazelemeden bu kadar süre geçmediyse ağa hiç çıkılmaz.
     * @return güncel (ya da bilinen) kota; hiç bilgi yoksa `null`.
     */
    suspend fun refresh(apiKey: String, maxAgeMillis: Long = 0L): MdbListQuota? {
        val key = apiKey.trim()
        if (key.isEmpty()) {
            clear()
            return null
        }
        val fingerprint = fingerprintOf(key)
        if (activeFingerprint != fingerprint) bind(appContext, key)

        val now = System.currentTimeMillis()
        if (maxAgeMillis > 0L && lastRefreshAtMillis > 0L && now - lastRefreshAtMillis < maxAgeMillis) {
            return _quota.value?.rolledOver(now)
        }

        return refreshMutex.withLock {
            val start = System.currentTimeMillis()
            if (maxAgeMillis > 0L && lastRefreshAtMillis > 0L && start - lastRefreshAtMillis < maxAgeMillis) {
                return@withLock _quota.value?.rolledOver(start)
            }
            lastRefreshAtMillis = start
            val fetched = try {
                MdbListClient.fetchQuota(key)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "MDBList kota tazelemesi başarısız (${e.javaClass.simpleName})")
                null
            }
            if (fetched != null) record(key, fetched)
            _quota.value?.rolledOver()
        }
    }

    /**
     * "Kota doldu" bilgi balonunun **yalnızca bir kez** gösterilmesini sağlar.
     *
     * Detay kartı ve ayarlar paneli aynı anda kompoze olabildiği için ikisi de ayrı ayrı
     * uyarı göstermemeli. Sıfırlanma anına göre damgalanır: yeni günde kota yeniden
     * dolarsa uyarı yine bir kez gösterilir.
     */
    fun consumeExhaustedNotice(): Boolean {
        val current = _quota.value?.rolledOver() ?: return false
        if (!current.isExhausted) return false
        val stamp = current.resetAtEpochSeconds ?: -1L
        synchronized(noticeLock) {
            if (lastNotifiedResetAt == stamp) return false
            lastNotifiedResetAt = stamp
            return true
        }
    }

    /** Ağ isteği yapmadan, bilinen kotaya göre günlük hakkın bittiğini söyler. */
    fun isExhausted(apiKey: String): Boolean {
        val fingerprint = fingerprintOf(apiKey) ?: return false
        if (fingerprint != activeFingerprint) return false
        return _quota.value?.rolledOver()?.isExhausted == true
    }

    /**
     * Herhangi bir MDBList yanıtının `X-RateLimit-*` başlıklarından gelen değerleri kaydeder.
     * `null` alanlar mevcut bilgiyi silmez (MDBList bazı yanıtlarda yalnızca birkaç başlık yollar).
     */
    internal fun recordHeaders(
        apiKey: String,
        limit: Int?,
        remaining: Int?,
        resetAtEpochSeconds: Long?,
        plan: MdbListPlan? = null
    ) {
        if (limit == null && remaining == null) return
        record(
            apiKey = apiKey,
            incoming = MdbListQuota(
                limit = limit,
                remaining = remaining,
                resetAtEpochSeconds = resetAtEpochSeconds,
                plan = plan ?: MdbListPlan.UNKNOWN,
                updatedAtMillis = System.currentTimeMillis()
            )
        )
    }

    /** 429 (kota aşımı) alındığında çağrılır: kalan 0 kabul edilir, sıfırlanma saati korunur. */
    internal fun recordExhausted(apiKey: String, resetAtEpochSeconds: Long?) {
        val current = _quota.value
        record(
            apiKey = apiKey,
            incoming = MdbListQuota(
                limit = current?.limit,
                remaining = 0,
                resetAtEpochSeconds = resetAtEpochSeconds ?: current?.resetAtEpochSeconds,
                plan = current?.plan ?: MdbListPlan.UNKNOWN,
                updatedAtMillis = System.currentTimeMillis()
            )
        )
    }

    internal fun record(apiKey: String, incoming: MdbListQuota) {
        val key = apiKey.trim()
        if (key.isEmpty()) return
        val fingerprint = fingerprintOf(key) ?: return
        when {
            // Henüz hiçbir anahtar bağlanmadıysa (ör. uygulama açılışında ilk puan isteği)
            // bu yanıt kotayı sahiplenebilir.
            activeFingerprint == null -> bind(appContext, key)
            // Farklı bir anahtarın yanıtı, bağlı anahtarın kotasını ezmemeli. (Örn. kullanıcı
            // ayarlarda yeni anahtar denerken vazgeçerse eski anahtarın sayısı korunur.)
            activeFingerprint != fingerprint -> return
        }

        val current = _quota.value?.rolledOver() ?: MdbListQuota()
        val merged = MdbListQuota(
            limit = incoming.limit ?: current.limit,
            remaining = incoming.remaining ?: current.remaining,
            resetAtEpochSeconds = incoming.resetAtEpochSeconds ?: current.resetAtEpochSeconds,
            plan = if (incoming.plan != MdbListPlan.UNKNOWN) incoming.plan else current.plan,
            updatedAtMillis = if (incoming.updatedAtMillis > 0L) incoming.updatedAtMillis else System.currentTimeMillis()
        )
        if (merged != current) {
            _quota.value = merged
            persist(fingerprint, merged)
        }
    }

    /** Özellik kapandığında ya da anahtar silindiğinde arayüzdeki değeri temizler. */
    fun clear() {
        activeFingerprint = null
        lastRefreshAtMillis = 0L
        _quota.value = null
    }

    private fun loadPersisted(fingerprint: String): MdbListQuota? {
        val prefs = preferences() ?: return null
        return runCatching {
            if (prefs.getString(KEY_FINGERPRINT, null) != fingerprint) return null
            val storedLimit = prefs.getInt(KEY_LIMIT, -1).takeIf { it > 0 }
            val storedRemaining = prefs.getInt(KEY_REMAINING, -1).takeIf { it >= 0 }
            if (storedLimit == null && storedRemaining == null) return null
            val storedPlan = MdbListPlan.entries.firstOrNull { it.name == prefs.getString(KEY_PLAN, null) }
                ?: MdbListPlan.UNKNOWN
            MdbListQuota(
                limit = storedLimit,
                remaining = storedRemaining,
                resetAtEpochSeconds = prefs.getLong(KEY_RESET, 0L).takeIf { it > 0L },
                plan = storedPlan,
                updatedAtMillis = prefs.getLong(KEY_UPDATED, 0L)
            ).rolledOver()
        }.getOrNull()
    }

    private fun persist(fingerprint: String, quota: MdbListQuota) {
        val prefs = preferences() ?: return
        runCatching {
            prefs.edit()
                .putString(KEY_FINGERPRINT, fingerprint)
                .putInt(KEY_LIMIT, quota.limit ?: -1)
                .putInt(KEY_REMAINING, quota.remaining ?: -1)
                .putLong(KEY_RESET, quota.resetAtEpochSeconds ?: 0L)
                .putString(KEY_PLAN, quota.plan.name)
                .putLong(KEY_UPDATED, quota.updatedAtMillis)
                .apply()
        }
    }

    private fun preferences(): android.content.SharedPreferences? {
        val context = appContext ?: return null
        return runCatching {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }.getOrNull()
    }

    private fun fingerprintOf(apiKey: String): String? {
        val trimmed = apiKey.trim()
        if (trimmed.isEmpty()) return null
        return MessageDigest.getInstance("SHA-256")
            .digest(trimmed.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
