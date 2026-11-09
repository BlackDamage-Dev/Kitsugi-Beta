package com.kitsugi.animelist.core.download

import java.net.URI
import java.util.Locale

/**
 * HLS (m3u8) çözümleme modeli ve ayrıştırıcısı.
 *
 * Neden ayrı bir dosya? İndirici içinde satır satır `Regex` ile playlist okumak
 * şu alanları tamamen kaçırıyordu:
 *
 *  - `EXT-X-KEY` (AES-128 şifreli segmentler) → indirilen dosya bozuk/oynatılamaz
 *  - `EXT-X-BYTERANGE` → tek dosyaya işaret eden segmentler yanlış indiriliyordu
 *  - `EXT-X-MEDIA-SEQUENCE` → IV türetimi ve anahtar eşlemesi kayıyordu
 *  - `#EXT-X-ENDLIST` yokluğu (canlı/bitmemiş akış) → yarım dosya "tamamlandı" sayılıyordu
 *
 * Bu ayrıştırıcı yalnızca **saf metin** ile çalışır; ağ erişimi yapmaz, böylece
 * birim testi kolaydır ve indirme akışından bağımsızdır.
 */

/** `EXT-X-BYTERANGE` bilgisi. `offset` verilmemişse bir önceki segmentin bitişini izler. */
data class HlsByteRange(val length: Long, val offset: Long)

/** `EXT-X-MAP` (fMP4 başlangıç bölümü). */
data class HlsInitSegment(
    val url: String,
    val byteRange: HlsByteRange? = null,
    val key: HlsKey? = null
)

/**
 * `EXT-X-KEY`.
 *
 * @param method  `NONE`, `AES-128`, `SAMPLE-AES`
 * @param uri     Anahtar adresi (METHOD != NONE ise zorunlu)
 * @param iv      `IV=0x...` varsa; yoksa segment sırasından türetilir.
 */
data class HlsKey(
    val method: String,
    val uri: String?,
    val iv: ByteArray?
) {
    val isEncrypted: Boolean get() = !method.equals("NONE", ignoreCase = true)

    // ByteArray içerdiği için veri sınıfı equals/hashCode'u elle yazılır.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HlsKey) return false
        return method.equals(other.method, ignoreCase = true) &&
            uri == other.uri &&
            (iv?.contentEquals(other.iv) ?: (other.iv == null))
    }

    override fun hashCode(): Int = (method.lowercase(Locale.ROOT).hashCode() * 31 + (uri?.hashCode() ?: 0)) * 31 + (iv?.contentHashCode() ?: 0)
}

/** Tek bir medya segmenti. */
data class HlsSegment(
    /** Mutlak URL (playlist adresine göre çözümlenmiş). */
    val url: String,
    /** Bu segmentin medya sırası (`EXT-X-MEDIA-SEQUENCE` + indeks). */
    val sequence: Long,
    val durationSeconds: Double = 0.0,
    val byteRange: HlsByteRange? = null,
    val key: HlsKey? = null,
    val discontinuity: Boolean = false
)

/** Medya (segment listesi) playlist'i. */
data class HlsMediaPlaylist(
    val url: String,
    val segments: List<HlsSegment>,
    val initSegment: HlsInitSegment?,
    /** `#EXT-X-ENDLIST` var mı? Yoksa akış canlı/bitmemiş olabilir. */
    val ended: Boolean,
    val targetDurationSeconds: Int,
    /** Reklam/kesinti işaretleri — yalnızca teşhis için. */
    val discontinuityCount: Int
) {
    val totalDurationSeconds: Double get() = segments.sumOf { it.durationSeconds }
    val encrypted: Boolean get() = segments.any { it.key?.isEncrypted == true }
}

/** Master playlist içindeki bir varyant (kalite) akışı. */
data class HlsVariant(
    val url: String,
    val bandwidth: Long,
    val averageBandwidth: Long?,
    val width: Int?,
    val height: Int?,
    val codecs: String?,
    val audioGroup: String?,
    val isAudioOnly: Boolean,
    /**
     * Varyantın video codec'i (`avc1.64001f`, `hvc1...`, `av01...`).
     * Cihaz uyumluluğu değerlendirmesinde kullanılır.
     */
    val videoCodec: String? = null,
    /** Varyantın ses codec'i (`mp4a.40.2`, `ec-3`, `ac-3`...). */
    val audioCodec: String? = null
)

/**
 * Varyant bu cihazda oynatılabilir mi?
 *
 * @param isSupported codec listesi → destekleniyor mu (test edilebilirlik için enjekte)
 */
fun HlsVariant.isPlayableOnThisDevice(
    isSupported: (String?) -> Boolean = DeviceCodecSupport::isCodecListSupported
): Boolean {
    if (codecs.isNullOrBlank()) return true // Bilgi yok → engellemeyelim.
    return isSupported(codecs)
}

/** Master playlist. */
data class HlsMasterPlaylist(
    val url: String,
    val variants: List<HlsVariant>,
    /** `EXT-X-MEDIA:TYPE=AUDIO` grupları (video ile ayrı ses akışı varsa). */
    val audioRenditions: List<HlsAudioRendition>
)

data class HlsAudioRendition(
    val groupId: String,
    val name: String?,
    val language: String?,
    val url: String?,
    val isDefault: Boolean
)

/**
 * Ayrıştırıcı günlük köprüsü.
 *
 * `android.util.Log` doğrudan çağrılırsa saf JVM birim testlerinde
 * ("not mocked") hata fırlar. Ayrıştırıcı **saf** kalmalı; bu yüzden log
 * çağrıları hem testte hem cihazda güvenli olan tek noktadan geçer.
 */
internal object HlsLog {
    /** Testlerde logları yakalamak için değiştirilebilir. */
    @Volatile
    var sink: ((String) -> Unit)? = null

    fun warn(tag: String, message: String) {
        sink?.invoke("$tag: $message")
            ?: runCatching { android.util.Log.w(tag, message) }
    }
}
