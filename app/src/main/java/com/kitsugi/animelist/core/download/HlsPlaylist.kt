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

object HlsPlaylistParser {

    private val TAG = "HlsPlaylistParser"

    /** Video codec kimliklerini tanımak için kaba desen. */
    private val CODE_C_Video = Regex("^(avc|hvc|hev|av01|vp0|dvh|mp4v)")

    /** Metin bir master (varyant listesi) playlist mi? */
    fun isMasterPlaylist(text: String): Boolean =
        text.lineSequence().any { it.trimStart().startsWith("#EXT-X-STREAM-INF", ignoreCase = true) }

    /**
     * Master playlist ayrıştırır. Master değilse `null` döner.
     */
    fun parseMaster(text: String, baseUrl: String): HlsMasterPlaylist? {
        if (!isMasterPlaylist(text)) return null

        val variants = mutableListOf<HlsVariant>()
        val audioRenditions = mutableListOf<HlsAudioRendition>()

        var pendingVariant: Map<String, String>? = null
        var pendingVariantLine = -1

        text.lineSequence().forEachIndexed { index, rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEachIndexed

            when {
                line.startsWith("#EXT-X-STREAM-INF", ignoreCase = true) -> {
                    pendingVariant = parseAttributes(line.substringAfter(':', ""))
                    pendingVariantLine = index
                }

                line.startsWith("#EXT-X-MEDIA:", ignoreCase = true) -> {
                    val attrs = parseAttributes(line.substringAfter(':', ""))
                    if (attrs["TYPE"].equals("AUDIO", ignoreCase = true)) {
                        audioRenditions += HlsAudioRendition(
                            groupId = attrs["GROUP-ID"].orEmpty(),
                            name = attrs["NAME"],
                            language = attrs["LANGUAGE"],
                            url = attrs["URI"]?.let { resolveUrl(baseUrl, it) },
                            isDefault = attrs["DEFAULT"].equals("YES", ignoreCase = true)
                        )
                    }
                }

                line.startsWith("#") -> Unit // diğer etiketler

                else -> {
                    val attrs = pendingVariant
                    if (attrs != null) {
                        val bandwidth = attrs["BANDWIDTH"]?.toLongOrNull() ?: 0L
                        val resolution = attrs["RESOLUTION"]
                        val (w, h) = parseResolution(resolution)
                        val codecs = attrs["CODECS"]
                        val audioGroup = attrs["AUDIO"]
                        variants += HlsVariant(
                            url = resolveUrl(baseUrl, line),
                            bandwidth = bandwidth,
                            averageBandwidth = attrs["AVERAGE-BANDWIDTH"]?.toLongOrNull(),
                            width = w,
                            height = h,
                            codecs = codecs,
                            audioGroup = audioGroup,
                            isAudioOnly = looksAudioOnly(codecs, h),
                            videoCodec = splitCodecs(codecs).firstOrNull { CODE_C_Video.containsMatchIn(it) },
                            audioCodec = splitCodecs(codecs).firstOrNull { it.startsWith("mp4a") || it.startsWith("ac-3") || it.startsWith("ec-3") || it.startsWith("opus") || it.startsWith("dts") || it.startsWith("flac") }
                        )
                        pendingVariant = null
                    } else {
                        // `#EXT-X-STREAM-INF` olmadan çıplak URI: bazı bozuk yayıncılar
                        // master'ı düz liste olarak sunuyor; sessizce yok saymak yerine
                        // tek varyant kabul et (aşağıdaki bant genişliği 0 olur).
                        variants += HlsVariant(
                            url = resolveUrl(baseUrl, line),
                            bandwidth = 0L,
                            averageBandwidth = null,
                            width = null,
                            height = null,
                            codecs = null,
                            audioGroup = null,
                            isAudioOnly = false
                        )
                    }
                }
            }
        }

        if (variants.isEmpty()) {
            HlsLog.warn(TAG, "Master playlist'te varyant bulunamadı (satır: $pendingVariantLine)")
            return null
        }
        return HlsMasterPlaylist(baseUrl, variants, audioRenditions)
    }

    /**
     * Kalite seçimi: en yüksek **video** varyantı. Yalnızca ses içeren varyantlar
     * (CODECS "mp4a" ve yükseklik yok) video indirmesinde işe yaramaz, atlanır.
     */
    @Deprecated("selectVariant kullanın", ReplaceWith("selectVariant(master)"))
    fun selectBestVariant(master: HlsMasterPlaylist): HlsVariant? =
        selectVariant(master, allowUnsupported = true)

    /**
     * Cihazın çalabildiği **en yüksek kaliteli** varyantı seçer.
     *
     * Neden? İndirilen dosya cihazda açılmıyorsa indirmenin hiçbir anlamı yok.
     * Kaynak bazen aynı bölümü AV1/HEVC + AC-3 gibi cihazda bulunmayan
     * kombinasyonlarla sunuyor; o varyantı seçmek "inmiş ama oynamıyor"
     * dosya üretir.
     *
     * Sıralama: desteklenen > bilinmeyen > desteklenmeyen; sonra bitrate.
     *
     * @param allowUnsupported Hiç desteklenen varyant yoksa en iyisine düş
     *   (çağıran katman kullanıcıyı uyarır ve seçim yine kaydedilir).
     * @return seçilen varyant; `null` → master'da video varyantı yok
     */
    fun selectVariant(
        master: HlsMasterPlaylist,
        allowUnsupported: Boolean = true,
        isCodecSupported: (String?) -> Boolean = DeviceCodecSupport::isCodecListSupported
    ): HlsVariant? {
        val videoVariants = master.variants.filterNot { it.isAudioOnly }
        val pool = if (videoVariants.isNotEmpty()) videoVariants else master.variants
        if (pool.isEmpty()) return null

        val supported = pool.filter {
            it.isPlayableOnThisDevice(isCodecSupported)
        }
        val chosenPool = when {
            supported.isNotEmpty() -> supported
            allowUnsupported -> pool
            else -> emptyList()
        }
        return chosenPool.maxWithOrNull(
            compareBy<HlsVariant> { it.bandwidth }
                .thenBy { it.averageBandwidth ?: 0L }
                .thenBy { it.height ?: 0 }
        )
    }

    fun selectVariant(
        master: HlsMasterPlaylist,
        isCodecSupported: (String?) -> Boolean
    ): HlsVariant? = selectVariant(master, allowUnsupported = true, isCodecSupported = isCodecSupported)

    /** `CODECS` listesini parçalar (boş/whitespace temizlenir). */
    internal fun splitCodecs(codecs: String?): List<String> =
        codecs?.split(',')?.map { it.trim().lowercase(Locale.ROOT) }?.filter { it.isNotEmpty() } ?: emptyList()

    /**
     * Medya playlist ayrıştırır.
     *
     * @throws IllegalArgumentException playlist hiç segment içermiyorsa.
     */
    fun parseMedia(text: String, baseUrl: String): HlsMediaPlaylist {
        var mediaSequence = 0L
        var targetDuration = 0
        var ended = false
        var pendingKey: HlsKey? = null
        var pendingDuration = 0.0
        var pendingRangeLength: Long? = null
        var pendingRangeOffset: Long? = null
        var pendingDiscontinuity = false
        var nextRangeOffset = 0L
        var initSegment: HlsInitSegment? = null
        var discontinuityCount = 0

        val segments = mutableListOf<HlsSegment>()

        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            if (line.startsWith("#")) {
                val upper = line.uppercase(Locale.ROOT)
                when {
                    upper.startsWith("#EXT-X-MEDIA-SEQUENCE:") -> {
                        mediaSequence = line.substringAfter(':').trim().toLongOrNull() ?: 0L
                    }

                    upper.startsWith("#EXT-X-TARGETDURATION:") -> {
                        targetDuration = line.substringAfter(':').trim().toDoubleOrNull()?.toInt() ?: 0
                    }

                    upper.startsWith("#EXT-X-ENDLIST") -> ended = true

                    upper.startsWith("#EXT-X-DISCONTINUITY") && !upper.startsWith("#EXT-X-DISCONTINUITY-SEQUENCE") -> {
                        pendingDiscontinuity = true
                        discontinuityCount++
                        nextRangeOffset = 0L
                    }

                    upper.startsWith("#EXT-X-KEY:") -> {
                        val attrs = parseAttributes(line.substringAfter(':', ""))
                        val method = attrs["METHOD"] ?: "NONE"
                        pendingKey = HlsKey(
                            method = method,
                            uri = attrs["URI"]?.let { resolveUrl(baseUrl, it) },
                            iv = attrs["IV"]?.let { parseIv(it) }
                        )
                        if (pendingKey?.isEncrypted == true && pendingKey?.uri.isNullOrBlank()) {
                            HlsLog.warn(TAG, "AES anahtarı URI'siz bildirildi — şifre çözme atlanacak")
                        }
                    }

                    upper.startsWith("#EXT-X-MAP:") -> {
                        val attrs = parseAttributes(line.substringAfter(':', ""))
                        val uri = attrs["URI"]
                        if (!uri.isNullOrBlank()) {
                            val range = attrs["BYTERANGE"]?.let { parseByteRangeValue(it) }
                            initSegment = HlsInitSegment(
                                url = resolveUrl(baseUrl, uri),
                                // `EXT-X-MAP:BYTERANGE` offset vermezse dosya başından okunur.
                                byteRange = range?.let { HlsByteRange(it.first, it.second ?: 0L) },
                                key = pendingKey?.takeIf { it.isEncrypted }
                            )
                        }
                    }

                    upper.startsWith("#EXTINF:") -> {
                        pendingDuration = line.substringAfter(':').substringBefore(',').trim().toDoubleOrNull() ?: 0.0
                    }

                    upper.startsWith("#EXT-X-BYTERANGE:") -> {
                        val parsed = parseByteRangeValue(line.substringAfter(':', "").trim())
                        if (parsed != null) {
                            pendingRangeLength = parsed.first
                            pendingRangeOffset = parsed.second
                        }
                    }

                    // Bilinmeyen etiketler yok sayılır (ANA SKIP / LOW-LATENCY vb.).
                    else -> Unit
                }
                continue
            }

            // URI satırı → segment
            val sequence = mediaSequence + segments.size
            val range = pendingRangeLength?.let { length ->
                val offset = pendingRangeOffset ?: nextRangeOffset
                nextRangeOffset = offset + length
                HlsByteRange(length, offset)
            }

            segments += HlsSegment(
                url = resolveUrl(baseUrl, line),
                sequence = sequence,
                durationSeconds = pendingDuration,
                byteRange = range,
                key = pendingKey?.takeIf { it.isEncrypted },
                discontinuity = pendingDiscontinuity
            )

            pendingDuration = 0.0
            pendingRangeLength = null
            pendingRangeOffset = null
            pendingDiscontinuity = false
        }

        require(segments.isNotEmpty()) { "Playlist içinde segment bulunamadı" }

        return HlsMediaPlaylist(
            url = baseUrl,
            segments = segments,
            initSegment = initSegment,
            ended = ended,
            targetDurationSeconds = targetDuration,
            discontinuityCount = discontinuityCount
        )
    }

    // ── Yardımcılar ───────────────────────────────────────────────────────

    /**
     * `KEY=VALUE,KEY="VALUE,with,comma"` biçimini güvenli ayrıştırır.
     * (Tırnak içindeki virgüller ayırıcı sayılmaz.)
     */
    internal fun parseAttributes(input: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        if (input.isBlank()) return out

        val sb = StringBuilder()
        var inQuotes = false
        val parts = mutableListOf<String>()
        for (ch in input) {
            when {
                ch == '"' -> {
                    inQuotes = !inQuotes
                    sb.append(ch)
                }
                ch == ',' && !inQuotes -> {
                    parts += sb.toString()
                    sb.setLength(0)
                }
                else -> sb.append(ch)
            }
        }
        if (sb.isNotEmpty()) parts += sb.toString()

        for (part in parts) {
            val key = part.substringBefore('=', "").trim()
            if (key.isEmpty()) continue
            val value = part.substringAfter('=', "").trim().removeSurrounding("\"")
            out[key.uppercase(Locale.ROOT)] = value
        }
        return out
    }

    /** `IV=0x…` (32 hex hane) → 16 bayt. Geçersizse null. */
    internal fun parseIv(raw: String): ByteArray? {
        val hex = raw.removePrefix("0x").removePrefix("0X")
        if (hex.length % 2 != 0 || hex.length > 32) return null
        return runCatching {
            val padded = hex.padStart(32, '0')
            ByteArray(16) { i -> padded.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        }.getOrNull()
    }

    /** `BYTERANGE="length[@offset]"` → (length, offset?) */
    internal fun parseByteRangeValue(raw: String): Pair<Long, Long?>? {
        val cleaned = raw.trim().removeSurrounding("\"")
        val length = cleaned.substringBefore('@').toLongOrNull() ?: return null
        val offset = cleaned.substringAfter('@', "").toLongOrNull()
        return length to offset
    }

    private fun parseResolution(raw: String?): Pair<Int?, Int?> {
        if (raw.isNullOrBlank()) return null to null
        val parts = raw.lowercase(Locale.ROOT).split('x')
        if (parts.size != 2) return null to null
        return parts[0].trim().toIntOrNull() to parts[1].trim().toIntOrNull()
    }

    private fun looksAudioOnly(codecs: String?, height: Int?): Boolean {
        if (height != null && height > 0) return false
        val c = codecs?.lowercase(Locale.ROOT) ?: return false
        val hasVideoCodec = c.contains("avc") || c.contains("hvc") || c.contains("hev") ||
            c.contains("vp0") || c.contains("vp9") || c.contains("av01") || c.contains("dvh")
        val hasAudioCodec = c.contains("mp4a") || c.contains("ac-3") || c.contains("ec-3") || c.contains("opus")
        return !hasVideoCodec && hasAudioCodec
    }

    /** RFC 3986 uyumlu göreli adres çözümü. */
    fun resolveUrl(baseUrl: String, reference: String): String {
        val ref = reference.trim()
        if (ref.startsWith("http://") || ref.startsWith("https://") || ref.startsWith("data:")) return ref
        return runCatching { URI(baseUrl).resolve(ref).toString() }.getOrElse {
            val noQuery = baseUrl.substringBefore('?')
            val slash = noQuery.lastIndexOf('/')
            if (slash >= 0) noQuery.substring(0, slash + 1) + ref else ref
        }
    }
}
