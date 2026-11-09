package com.kitsugi.animelist.core.download

import java.net.URI
import java.util.Locale

/**
 * T-IND-3 (2026-11-09): saf JVM HLS playlist ayrıştırıcısı (indirme hattı için).
 *
 * NEDEN AYRI VE SAF: eski indirme kodu playlist'i satır satır "kabaca" okuyordu ve iki
 * standard özelliği YOK SAYIYORDU:
 *  1. `#EXT-X-BYTERANGE`: tek dosyanın aralıklarıyla kurulan playlist'lerde her satır aynı
 *     URL'yi verir → eski kod aynı dosyayı N kez indirip birleştiriyor = dev bozuk dosya.
 *  2. `#EXT-X-KEY` (AES-128): şifreli segmentler ŞİFRELİ hâlde dosyaya yazılıyordu =
 *     hiçbir oynatıcının açamadığı "çöp" dosya. (SAMPLE-AES/WIDEVINE gibi DRM'li yöntemler
 *     ise indirilemez; artık temiz bir hata mesajıyla reddediliyor.)
 * Bu nesne Android bağımlılığı olmadığı için birim testlerle doğrulanır.
 */
object HlsPlaylistParser {

    private const val TAG = "HlsPlaylistParser"

    /** Video codec kimliklerini tanımak için kaba desen. */
    private val CODE_C_Video = Regex("^(avc|hvc|hev|av01|vp0|dvh|mp4v)")

    data class Segment(
        val url: String,
        /** BYTERANGE başlangıç baytı; -1 = aralık yok (tam dosya/segment). */
        val rangeStart: Long = -1L,
        /** BYTERANGE uzunluğu; -1 = aralık yok. */
        val rangeLen: Long = -1L,
        /** AES-128 anahtar adresi; null = şifresiz. */
        val keyUri: String? = null,
        /** `IV=0x…` değeri; null ise IV = media sequence (16 bayt big-endian). */
        val keyIvHex: String? = null,
        /** Media sequence numarası (IV türetmede kullanılır). */
        val seq: Int = 0
    ) {
        val hasRange: Boolean get() = rangeStart >= 0L && rangeLen > 0L
    }

    data class Plan(
        val mediaPlaylistUrl: String,
        val segments: List<Segment>,
        val initSegmentUrl: String?,
        /** DRM (SAMPLE-AES vb.) gibi indirilemeyen durumların kullanıcıya dönük nedeni. */
        val unsupportedReason: String? = null
    )

    fun resolveUrl(baseUrl: String, relativeUrl: String): String {
        val ref = relativeUrl.trim()
        if (ref.startsWith("http://") || ref.startsWith("https://") || ref.startsWith("data:")) return ref
        return runCatching { URI(baseUrl).resolve(ref).toString() }.getOrElse {
            val noQuery = baseUrl.substringBefore('?')
            val slash = noQuery.lastIndexOf('/')
            if (slash >= 0) noQuery.substring(0, slash + 1) + ref else ref
        }
    }

    fun isMasterPlaylist(content: String): Boolean =
        content.lineSequence().any { it.trimStart().startsWith("#EXT-X-STREAM-INF", ignoreCase = true) }

    /** Master listesinden EN YÜKSEK bant genişlikli (eşitse çözünürlüklü) varyantı seçer. */
    fun pickVariantUrl(masterContent: String, masterUrl: String): String? {
        var best: String? = null
        var bestScore = -1L
        var pending = -1L
        for (raw in masterContent.lines()) {
            val line = raw.trim()
            if (line.startsWith("#EXT-X-STREAM-INF", ignoreCase = true)) {
                pending = variantScore(line)
                continue
            }
            if (line.isEmpty() || line.startsWith("#")) continue
            val score = if (pending >= 0) pending else 0L
            pending = -1L
            val url = resolveUrl(masterUrl, line)
            if (score > bestScore) {
                bestScore = score
                best = url
            }
        }
        return best
    }

    private fun variantScore(line: String): Long {
        val bw = Regex("""BANDWIDTH=(\d+)""").find(line)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val height = Regex("""RESOLUTION=\d+x(\d+)""").find(line)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        return bw / 1000 + height * 10_000
    }

    /** Media playlist'i ayrıştırır: segmentler + init + anahtar/arıklık durumu. */
    fun parseMediaPlaylist(content: String, playlistUrl: String): Plan {
        var seq = 0
        var keyUri: String? = null
        var keyIv: String? = null
        var unsupported: String? = null
        var init: String? = null
        var pendingRange: Pair<Long, Long>? = null
        var lastRangeEnd = 0L
        val segments = mutableListOf<Segment>()

        for (raw in content.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            when {
                line.startsWith("#EXT-X-MEDIA-SEQUENCE:") ->
                    line.substringAfter(':').trim().toIntOrNull()?.let { seq = it }

                line.startsWith("#EXT-X-KEY:") -> {
                    val method = attr(line, "METHOD")?.trim()
                    when {
                        method == null || method.equals("NONE", true) -> {
                            keyUri = null
                            keyIv = null
                        }
                        method.equals("AES-128", true) -> {
                            keyUri = attr(line, "URI")?.unquote()
                            keyIv = attr(line, "IV")?.trim()
                        }
                        else -> unsupported =
                            "Bu akış DRM ile şifreli ($method); telif/korumalı içerik indirilemez."
                    }
                }

                line.startsWith("#EXT-X-BYTERANGE:") -> {
                    val spec = line.substringAfter(':').trim()
                    val len = spec.substringBefore('@').toLongOrNull()
                    if (len != null && len > 0) {
                        val off = spec.substringAfter('@', "").trim().toLongOrNull() ?: lastRangeEnd
                        pendingRange = off to len
                    }
                }

                line.startsWith("#EXT-X-MAP:") ->
                    attr(line, "URI")?.unquote()?.let { init = resolveUrl(playlistUrl, it) }

                line.startsWith("#") -> Unit

                else -> {
                    val range = pendingRange
                    if (range != null) lastRangeEnd = range.first + range.second
                    segments.add(
                        Segment(
                            url = resolveUrl(playlistUrl, line),
                            rangeStart = range?.first ?: -1L,
                            rangeLen = range?.second ?: -1L,
                            keyUri = keyUri,
                            keyIvHex = keyIv,
                            seq = seq
                        )
                    )
                    seq++
                    pendingRange = null
                }
            }
        }
        return Plan(playlistUrl, segments, init, unsupported)
    }

    /**
     * AES-128 IV'si: playlist açık `IV=0x…` verdiyse o; verilmediyse segmentin media
     * sequence numarasının 16 bayt big-endian temsili (RFC 8216 §4.3.4.4).
     */
    fun ivBytesFor(seg: Segment): ByteArray {
        val hex = seg.keyIvHex?.removePrefix("0x")?.removePrefix("0X")?.trim()
        if (!hex.isNullOrBlank() && hex.length >= 2) {
            val clean = hex.takeLast(32).padStart(32, '0')
            return ByteArray(16) { i ->
                clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        }
        val out = ByteArray(16)
        var v = seg.seq.toLong()
        for (i in 15 downTo 12) {
            out[i] = (v and 0xFF).toByte()
            v = v shr 8
        }
        return out
    }

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

                    else -> Unit
                }
                continue
            }

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

    /**
     * `KEY=VALUE,KEY="VALUE,with,comma"` biçimini güvenli ayrıştırır.
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

    private fun String.unquote(): String = trim().trim('"', '\'')

    /** `TAG:AD=değer,AD2="değer2"` satırından öznitelik değerini (tırnaklıysa tırnaklarıyla) alır. */
    private fun attr(line: String, name: String): String? {
        val key = "$name="
        val idx = line.indexOf(key)
        if (idx < 0) return null
        val start = idx + key.length
        if (start >= line.length) return null
        if (line[start] == '"') {
            val end = line.indexOf('"', start + 1)
            if (end < 0) return null
            return line.substring(start, end + 1)
        }
        var end = line.indexOf(',', start)
        if (end < 0) end = line.length
        return line.substring(start, end)
    }
}
