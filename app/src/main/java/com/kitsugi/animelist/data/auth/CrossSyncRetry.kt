package com.kitsugi.animelist.data.auth

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/**
 * Çapraz eşitleme yazma (write) sırasında **kaynaklara özgü geçici hatalar için** ortak
 * yeniden deneme (retry) politikası.
 *
 * Her sağlayıcı farklı şekillerde hata bildirir:
 *  - MAL / AniList: HTTP hata → `IllegalStateException`; ağ hatası → `IllegalStateException` ("bağlantı hatası")
 *  - Kitsu: HTTP hata → [KitsuSyncManager.SyncResult.errors] metni; ağ hatası → `IOException` (RetryInterceptor)
 *  - Shikimori: HTTP hata → null → [ShikimoriSyncManager.SyncResult.errors] metni; ağ hatası → `IOException`
 *  - Bangumi: HTTP hata → [BangumiApiClient.BangumiApiException]; ağ hatası → `IOException`
 *  - Simkl: zaten `postSyncEnvelope` içinde 429/ağ için yeniden dener; burada tekrar eklenmez.
 *
 * Bu nesne tüm geçici (tekrarlanabilir) hataları ortak bir sınıflandırma altına alır; her deneme
 * öncesi [PlatformRateLimiter.acquire] uygulanır, denemeler arası üstel geri çekilme ve korutürün
 * sonlanması kontrolü yapılır. Kalıcı (4xx kimlik/eksiklik, kimlik reddi, eşleşme yok) hatalar
 * yeniden denenmez — bunlar "atlama" ya da gerçek kalıcı hatalardır.
 */
object CrossSyncRetry {
    private const val TAG = "CrossSyncRetry"

    /** Tekrarlanması makul olan HTTP durum kodları (408 İleride Çalışacak, 425 Çok Erken, 429). */
    private val transientHttpCodes = setOf(408, 425, 429)

    /** HTTP kodu arnavuz context'leri (mesaj içinde HTTP/HTTP-kodu ile karşılaşınca). */
    private val transientHttpContextPatterns = listOf(
        Regex("""(?i)\bHTTP\s*(408|425|429|5\d{2})\b"""),
        Regex("""(?i)\bAPI hatası:\s*(408|425|429|5\d{2})\b"""),
        Regex("""(?i)\b(?:başarısız|hata|error|status)\s*[:]?\s*(408|425|429|5\d{2})\b"""),
    )

    /** Bağımsız anahtar kelimeler (bağlantı/sunucu geçiciliği). */
    private val transientKeywords = listOf(
        // Hız sınırı
        "hız sınırı", "hız sınırlama", "rate limit", "ratelimited", "too many requests",
        "istek sınırına takıldı",
        // Ağ / transport
        "bağlantı hatası", "connection error", "connection refused", "connection reset",
        "connection timed out", "failed to connect", "unable to resolve host", "unknown host",
        "network is unreachable", "software caused connection abort", "sockettimeout",
        "timed out", "timeout", "connectexception", "ssl", "no route to host",
        // Sunucu geçiciliği
        "geçici hata", "temporarily disabled", "service down", "sunucu geçici",
        "server error", "internal server error", "bad gateway", "service unavailable",
    )

    /**
     * Bir exception'ın çapraz eşitleme yazması için geçici (tekrarlanabilir) hata mı olduğuna karar verir.
     * [CancellationException] önce dışarı atılır — cancel bir kontrol akışı, hata değildir.
     */
    fun Throwable.isTransientSyncError(): Boolean {
        if (this is CancellationException) return false
        // Ağ / transport katmanı hataları → her zaman yeniden denebilir.
        if (this is java.io.IOException) return true
        // AniList sunucu tarafında geçici kapalı (HTTP 403 + "temporarily disabled").
        if (this::class.simpleName == "AniListServiceDownException") return true
        // Ortak hız sınırı exception'ları.
        if (this is com.kitsugi.animelist.data.remote.RateLimitException) return true
        if (this is com.kitsugi.animelist.data.repository.SimklRateLimitException) return true
        // Bangumi HTTP hata — yapısal HTTP kodu var.
        if (this is BangumiApiClient.BangumiApiException) {
            return code in transientHttpCodes || code in 500..599
        }
        // Kalanlar: mesaj içerik analizi.
        return isTransientErrorText(message ?: return false)
    }

    /** Hata metninin (ör. [KitsuSyncManager.SyncResult.errors] string'i) geçici hata metni mi olduğunu belirler. */
    fun isTransientErrorText(text: String): Boolean {
        if (text.isBlank()) return false
        for (pattern in transientHttpContextPatterns) {
            if (pattern.containsMatchIn(text)) return true
        }
        val lower = text.lowercase()
        return transientKeywords.any { lower.contains(it) }
    }

    /**
     * Bir yazma işlemini çalıştırır; geçici hatalarda [attempts] kez yeniden dener.
     *
     * İki türlü geçici başarısızlık yeniden dener:
     *  1. Bloktan fırlayan exception geçici ise (ağ, 5xx, 429…),
     *  2. Blok başarılı döndü ama [errorTexts] ile çıkarılan hata metinleri arasında geçici olan varsa
     *     (ör. Kitsu HTTP 5xx'i [KitsuSyncManager.SyncResult.errors] içinde döner, exception değil).
     * Kalıcı hatalar (4xx kimlik/eksiklik, kimlik reddi, eşleşme yok) hemen döndürülür, yeniden denmez.
     *
     * @param platform Hız sınırlayıcı anahtarı (ör. "kitsu", "anilist", "mal", "shikimori", "bangumi").
     * @param description Rapor/günlük için insan okunabilir açıklama.
     * @param attempts Toplam deneme sayısı (ilk deneme dahil).
     * @param initialDelayMs İlk geri çekilme süresi; sonraki denemeler üstel (2^attempt) artar.
     * @param errorTexts Döndürülen değerden hata metinlerini çıkaran predikat. Geçici metin varsa yeniden dener.
     * @param block Yazıma götüren suspend iş.
     * @return Başarılı sonuç; aksi halde son denemedeki exception (metin hatası için başarılı sonuç + hata metinleri).
     */
    suspend fun <T> retrySyncWrite(
        platform: String,
        description: String,
        attempts: Int = 3,
        initialDelayMs: Long = 600L,
        errorTexts: (T) -> List<String> = { emptyList() },
        block: suspend () -> T
    ): Result<T> {
        var attempt = 0
        var lastValue: T? = null
        var lastException: Throwable? = null
        var sawTransientText = false

        while (attempt < attempts) {
            attempt++
            PlatformRateLimiter.acquire(platform)
            val outcome = runSyncCatching { block() }
            if (outcome.isSuccess) {
                val value = outcome.getOrThrow()
                val transientTexts = value?.let(errorTexts)?.filter { isTransientErrorText(it) }.orEmpty()
                if (transientTexts.isEmpty()) {
                    return outcome
                }
                lastValue = value
                sawTransientText = true
                Log.w(
                    TAG,
                    "[$platform] $description: geçici hata (${transientTexts.first()}); yeniden deneme $attempt/$attempts"
                )
            } else {
                val ex = outcome.exceptionOrNull()
                if (ex == null || !ex.isTransientSyncError()) {
                    return outcome
                }
                lastException = ex
                Log.w(
                    TAG,
                    "[$platform] $description: geçici exception (${ex?.javaClass?.simpleName}: ${ex?.message}); yeniden deneme $attempt/$attempts"
                )
            }
            if (attempt < attempts) {
                currentCoroutineContext().ensureActive()
                delay(initialDelayMs * (1L shl (attempt - 1)))
            }
        }

        if (lastException != null) {
            return Result.failure(lastException)
        }
        if (sawTransientText && lastValue != null) {
            // Tüm denemeler geçici metin hatalarla bitti: değeri (hata listesiyle) döndür,
            // çağıran taraf onu hata olarak değerlendirsin.
            @Suppress("UNCHECKED_CAST")
            return Result.success(lastValue as T)
        }
        return Result.failure(
            IllegalStateException("CrossSyncRetry: hiçbir deneme gerçekleşmedi ($description)")
        )
    }
}
