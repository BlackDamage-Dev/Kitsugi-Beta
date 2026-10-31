package com.kitsugi.animelist.data.platform

import java.util.concurrent.ConcurrentHashMap

/**
 * Kitsugi — liste kaynağı (platform) başarısızlıklarının son durumu.
 *
 * Ağ katmanı (HTTP 4xx/5xx) ve detay yükleyicileri başarısız bir istek gördüğünde
 * bunu [report] ile buraya bildirir. Detay / yükleme ekranları ise [latest] ile
 * "bu kaynak son dakikalarda hangi `KTS-…` koduyla başarısız oldu?" sorusunu sorar
 * ve kodu, nedenini ve çözümünü kullanıcıya gösterir.
 *
 * Tasarım notları:
 *  - Her platform için yalnızca **en son** başarısızlık tutulur (bellek sınırlı).
 *  - Eski kayıtlar [DEFAULT_MAX_AGE_MS] sonra yok sayılır; böylece dünkü bir hata
 *    bugünkü boş bir sayfaya yanlış kod olarak yapışmaz.
 *  - Başarılı istekler bu kaydı silmez: aynı kaynaktaki başka bir çağrının başarısı,
 *    asıl detay isteğinin hatasını gizlememelidir. Başarı durumu ilgili ViewModel'de tutulur.
 */
object PlatformFailureRegistry {

    /** Son başarısızlık: kod + ham neden (varsa) + zaman damgası. */
    data class Failure(
        val code: PlatformErrorCode,
        val rawReason: String?,
        val atMillis: Long,
    )

    /** Bir hatanın hâlâ "güncel" sayılacağı süre. */
    const val DEFAULT_MAX_AGE_MS: Long = 2 * 60_000L

    private val latestByPlatform = ConcurrentHashMap<ListPlatform, Failure>()

    /**
     * Bir başarısızlığı kaydeder ve sınıflandırılmış kodu döndürür.
     *
     * @param platformLabel Kaynak adı (`"simkl"`, `"Bangumi"`…). Tanınmazsa kayıt yapılmaz ve null döner.
     * @param error         Yakalanan istisna (iptal istisnaları kaydedilmez).
     * @param httpCode      Biliniyorsa HTTP durum kodu.
     * @param rawMessage    Ek bağlam; ham neden olarak da saklanır. Sorgu parametreleri
     *                      (API anahtarları) buraya yazılmamalıdır.
     */
    fun report(
        platformLabel: String?,
        error: Throwable? = null,
        httpCode: Int? = null,
        rawMessage: String? = null,
    ): PlatformErrorCode? {
        if (error is kotlinx.coroutines.CancellationException) return null
        val platform = ListPlatform.fromLabel(platformLabel)
        if (platform == ListPlatform.GENERIC) return null
        if (error == null && httpCode == null && rawMessage.isNullOrBlank()) {
            return latest(platformLabel)?.code
        }
        return record(platform, error, httpCode, rawMessage)
    }

    /** Ağ katmanından: istek ana bilgisayarına göre platform bulunur. */
    fun reportHttp(host: String?, httpCode: Int, path: String?): PlatformErrorCode? {
        val platform = ListPlatform.fromHost(host)
        if (platform == ListPlatform.GENERIC) return null
        val raw = "HTTP $httpCode · ${host.orEmpty()}${path.orEmpty()}"
        return record(platform, error = null, httpCode = httpCode, rawMessage = raw)
    }

    /**
     * Verilen kaynak için son [maxAgeMs] içindeki başarısızlığı döndürür.
     * Kaynak tanınmıyorsa veya kayıt yoksa / eskiyse null.
     */
    fun latest(
        platformLabel: String?,
        maxAgeMs: Long = DEFAULT_MAX_AGE_MS,
        now: Long = System.currentTimeMillis(),
    ): Failure? {
        val platform = ListPlatform.fromLabel(platformLabel)
        if (platform == ListPlatform.GENERIC) return null
        val failure = latestByPlatform[platform] ?: return null
        return failure.takeIf { now - it.atMillis <= maxAgeMs }
    }

    /**
     * Bir istisnayı bildirir ve kullanıcıya gösterilecek kodu döndürür.
     * İptal istisnalarında yeni kayıt yapılmaz; bilinmeyen durumda [PlatformErrorCode.COM_1020].
     */
    fun reportAndResolve(platformLabel: String?, error: Throwable?): PlatformErrorCode {
        if (error is kotlinx.coroutines.CancellationException) return PlatformErrorCode.COM_1020
        report(platformLabel, error = error)
        return currentOrUnknown(platformLabel)
    }

    /**
     * Kaynağın son güncel kodu; yoksa [PlatformErrorCode.COM_1020].
     * Kaynak hata fırlatmadan boş döndüğünde (istemci içinde yutulan hatalar) kullanılır.
     */
    fun currentOrUnknown(platformLabel: String?): PlatformErrorCode =
        latest(platformLabel)?.code ?: PlatformErrorCode.COM_1020

    /** [sinceMillis] anından beri her kaynağın son başarısızlık kodu (kaynak → kod). */
    fun failuresSince(sinceMillis: Long): Map<ListPlatform, PlatformErrorCode> =
        latestByPlatform.filterValues { it.atMillis >= sinceMillis }.mapValues { it.value.code }

    /** [sinceMillis] anından beri kaydedilen en son başarısızlık. */
    fun mostRecentSince(sinceMillis: Long): Failure? =
        latestByPlatform.values.filter { it.atMillis >= sinceMillis }.maxByOrNull { it.atMillis }

    /**
     * [sinceMillis] anından beri kaydedilen en son kaynak hatasının kodu; yoksa
     * istisnanın kendisinden sınıflandırma. Platformu belli olmayan akışlar için.
     */
    fun resolveSince(sinceMillis: Long, error: Throwable?): PlatformErrorCode =
        mostRecentSince(sinceMillis)?.code
            ?: error?.let { PlatformErrorCatalog.classify(error = it) }
            ?: PlatformErrorCode.COM_1020

    /** Test ve oturum sıfırlama için. */
    fun clear() {
        latestByPlatform.clear()
    }

    private fun record(
        platform: ListPlatform,
        error: Throwable?,
        httpCode: Int?,
        rawMessage: String?,
    ): PlatformErrorCode {
        val now = System.currentTimeMillis()
        val code = PlatformErrorCatalog.classify(
            platform = platform,
            error = error,
            httpCode = httpCode,
            rawMessage = rawMessage,
        )
        // Ağ katmanının kaydettiği somut HTTP/platform kodunu üst katmandaki sarmalanmış
        // belirsiz (COM_1020) hata ile ezme; taze somut tanı her zaman daha değerlidir.
        if (code == PlatformErrorCode.COM_1020) {
            val existing = latestByPlatform[platform]
            if (existing != null &&
                existing.code != PlatformErrorCode.COM_1020 &&
                now - existing.atMillis <= DEFAULT_MAX_AGE_MS
            ) {
                return existing.code
            }
        }
        latestByPlatform[platform] = Failure(
            code = code,
            rawReason = rawMessage?.takeIf { it.isNotBlank() } ?: error?.message,
            atMillis = now,
        )
        // v2.4.342: tanı kaydı **kalıcı** günlüğe de yazılır. Kullanıcı sonradan
        // "hangi kaynak, hangi kod, ne zaman" sorusunu günlükten cevaplayabilir.
        runCatching {
            com.kitsugi.animelist.core.diagnostics.KitsugiLog.log(
                level = com.kitsugi.animelist.core.diagnostics.KitsugiLog.Level.WARN,
                channel = com.kitsugi.animelist.core.diagnostics.KitsugiLog.Channel.NET,
                tag = platform.name.lowercase(),
                message = "hata kodu ${code.code} · ${platform.codePrefix}",
                detail = rawMessage?.takeIf { it.isNotBlank() }
                    ?: error?.let { "${it.javaClass.simpleName}: ${it.message}" },
                throwable = null
            )
        }
        return code
    }
}
