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

    /** Bilinen medya/CDN alan adları (API değil). */
    private val MEDIA_HOSTS = setOf(
        "image.tmdb.org",
        "api-cdn.myanimelist.net",
        "cdn.myanimelist.net",
        "media.kitsu.app",
        "media.kitsu.io",
        "simkl.in",
        "img.anili.st",
        "lain.bgm.tv",
    )

    /** CDN olduğu alan adı ön ekinden anlaşılan host'lar (`media.*`, `img1.*`, `cdn.*`…). */
    private val MEDIA_HOST_PREFIXES = listOf(
        "media.", "img.", "img", "cdn.", "static.", "images.", "image.", "assets.",
    )

    /** AniList poster CDN'i `s4.anilist.co` gibi numaralı alt alan adları kullanır. */
    private val ANILIST_CDN_HOST = Regex("^s\\d+\\.anilist\\.co$")

    /** Statik medya dosya uzantıları (sorgu dizesi hariç yol üzerinden bakılır). */
    private val MEDIA_EXTENSIONS = listOf(
        ".jpg", ".jpeg", ".png", ".webp", ".gif", ".avif", ".bmp", ".svg", ".ico",
        ".mp4", ".webm", ".m3u8", ".mpd", ".vtt", ".srt", ".ass",
    )

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

    /**
     * Ağ katmanından: istek ana bilgisayarına göre platform bulunur.
     *
     * Görsel/video CDN istekleri (poster, kapak, altyazı) buraya **kaydedilmez**:
     * bozuk tek bir poster 404'ü, kaynağın tamamı çökmüş gibi görünmesine yol açıyordu
     * (bkz. [isMediaAssetRequest]).
     */
    fun reportHttp(
        host: String?,
        httpCode: Int,
        path: String?,
        diagnosticHint: String? = null,
    ): PlatformErrorCode? {
        if (isMediaAssetRequest(host, path)) return null
        val platform = ListPlatform.fromHost(host)
        if (platform == ListPlatform.GENERIC) return null
        // Keep this an allow-listed marker rather than arbitrary response text. Simkl returns
        // HTTP 400 for its sync write-lock limit, which cannot be identified from status alone.
        val safeHint = diagnosticHint?.takeIf { it.equals("rate_limit", ignoreCase = true) }
        val raw = buildString {
            append("HTTP ").append(httpCode).append(" · ").append(host.orEmpty()).append(path.orEmpty())
            safeHint?.let { append(" · ").append(it) }
        }
        return record(platform, error = null, httpCode = httpCode, rawMessage = raw)
    }

    /**
     * İstek bir API çağrısı değil de statik medya (poster/kapak/video/altyazı) mı?
     *
     * NEDEN: [ListPlatform.fromHost] alan adı son ekiyle eşleşir; bu yüzden
     * `media.kitsu.app`, `s4.anilist.co`, `image.tmdb.org` gibi CDN'ler de
     * Kitsu/AniList/TMDB sayılıyordu. Süresi geçmiş tek bir poster URL'si 404
     * döndüğünde arama ekranı "Kayıt bulunamadı (HTTP 404)" rozeti basıyor,
     * sonuçlar ise sorunsuz listeleniyordu.
     */
    internal fun isMediaAssetRequest(host: String?, path: String?): Boolean {
        val normalizedHost = host?.trim()?.lowercase(java.util.Locale.ROOT)?.trimEnd('.').orEmpty()
        if (normalizedHost.isNotEmpty()) {
            if (MEDIA_HOSTS.contains(normalizedHost)) return true
            if (MEDIA_HOST_PREFIXES.any { normalizedHost.startsWith(it) }) return true
            if (ANILIST_CDN_HOST.matches(normalizedHost)) return true
        }
        val normalizedPath = path?.trim()?.lowercase(java.util.Locale.ROOT).orEmpty()
        if (normalizedPath.isEmpty()) return false
        return MEDIA_EXTENSIONS.any { normalizedPath.endsWith(it) }
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
