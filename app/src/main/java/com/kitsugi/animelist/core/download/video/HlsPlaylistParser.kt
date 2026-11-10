package com.kitsugi.animelist.core.download.video

import java.util.Locale

/** HLS ana (master) listesindeki bir kalite varyantı. */
internal data class HlsVariant(
    val url: String,
    val bandwidth: Long = 0L,
    val width: Int = 0,
    val height: Int = 0
)

/** `#EXT-X-KEY` — AES-128 şifreli parçalar için çözümleme bilgisi. */
internal data class HlsKey(
    val method: String,
    val uri: String?,
    val ivHex: String?
)

/** Medya listesindeki tek bir parça (segment). */
internal data class HlsSegment(
    val url: String,
    val byteRange: LongRange? = null,
    val sequence: Int = 0,
    val key: HlsKey? = null
)

/** Ayrıştırılmış medya listesi. */
internal data class HlsMediaPlaylist(
    val segments: List<HlsSegment> = emptyList(),
    val initSegmentUrl: String? = null,
    val initSegmentRange: LongRange? = null,
    val initSegmentKey: HlsKey? = null
) {
    /** `#EXT-X-MAP` varsa akış fMP4'tür → çıktı `.mp4`, yoksa MPEG-TS → `.ts`. */
    val isFragmentedMp4: Boolean get() = initSegmentUrl != null
}

/**
 * `.m3u8` çözümleyicisi.
 *
 * Neden ayrı ve saf: HLS indirme hatalarının neredeyse tamamı liste ayrıştırmasında
 * başlıyor (yanlış varyant, `#EXT-X-MAP` atlanınca oynatılamayan dosya, göreli
 * adreslerin çözülmemesi). Ağ kodundan ayırınca bu mantık JVM testleriyle doğrulanabiliyor.
 */
internal object HlsPlaylistParser {

    fun isMasterPlaylist(content: String): Boolean =
        content.lineSequence().any { it.trim().startsWith("#EXT-X-STREAM-INF", ignoreCase = true) }

    fun parseMaster(content: String, baseUrl: String): List<HlsVariant> {
        val variants = mutableListOf<HlsVariant>()
        var pendingBandwidth = 0L
        var pendingWidth = 0
        var pendingHeight = 0
        var pending = false

        fun flush(uri: String) {
            val resolved = VideoSourceClassifier.resolveUrl(baseUrl, uri)
            if (resolved.isNotBlank()) {
                variants += HlsVariant(
                    url = resolved,
                    bandwidth = pendingBandwidth,
                    width = pendingWidth,
                    height = pendingHeight
                )
            }
            pending = false
            pendingBandwidth = 0L
            pendingWidth = 0
            pendingHeight = 0
        }

        content.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach
            when {
                line.startsWith("#EXT-X-STREAM-INF", ignoreCase = true) -> {
                    val attrs = parseAttributes(line.substringAfter(':', ""))
                    pendingBandwidth = attrs["BANDWIDTH"]?.toLongOrNull() ?: 0L
                    attrs["RESOLUTION"]?.let { resolution ->
                        val parts = resolution.lowercase(Locale.ROOT).split('x')
                        if (parts.size == 2) {
                            pendingWidth = parts[0].trim().toIntOrNull() ?: 0
                            pendingHeight = parts[1].trim().toIntOrNull() ?: 0
                        }
                    }
                    pending = true
                }
                line.startsWith("#") -> Unit
                pending -> flush(line)
            }
        }
        return variants
    }

    /**
     * Cihaz için en uygun varyantı seçer: 1080p'yi aşmayan en yüksek çözünürlük,
     * eşitlikte en yüksek bant genişliği. Hiçbiri 1080p altında değilse en küçüğü.
     */
    fun pickBestVariant(variants: List<HlsVariant>): HlsVariant? {
        if (variants.isEmpty()) return null
        val capped = variants.filter { it.height in 1..1080 }
        if (capped.isNotEmpty()) {
            return capped.maxWithOrNull(
                compareBy<HlsVariant> { it.height }.thenBy { it.bandwidth }
            )
        }
        return variants.minWithOrNull(
            compareBy<HlsVariant> { if (it.height > 0) it.height else Int.MAX_VALUE }
                .thenByDescending { it.bandwidth }
        )
    }

    fun parseMedia(content: String, baseUrl: String): HlsMediaPlaylist {
        val segments = mutableListOf<HlsSegment>()
        var initUrl: String? = null
        var initRange: LongRange? = null
        var initKey: HlsKey? = null
        var currentKey: HlsKey? = null
        var pendingRange: LongRange? = null
        var sequence = 0
        var lastRangeEnd = -1L

        content.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach
            when {
                line.startsWith("#EXT-X-MAP", ignoreCase = true) -> {
                    val attrs = parseAttributes(line.substringAfter(':', ""))
                    attrs["URI"]?.let { uri ->
                        initUrl = VideoSourceClassifier.resolveUrl(baseUrl, uri)
                        initKey = currentKey
                        initRange = attrs["BYTERANGE"]?.let { parseByteRange(it, -1L) }
                    }
                }
                line.startsWith("#EXT-X-KEY", ignoreCase = true) -> {
                    val attrs = parseAttributes(line.substringAfter(':', ""))
                    val method = attrs["METHOD"].orEmpty().uppercase(Locale.ROOT)
                    currentKey = if (method.isEmpty() || method == "NONE") {
                        null
                    } else {
                        HlsKey(
                            method = method,
                            uri = attrs["URI"]?.let { VideoSourceClassifier.resolveUrl(baseUrl, it) },
                            ivHex = attrs["IV"]
                        )
                    }
                }
                line.startsWith("#EXT-X-BYTERANGE", ignoreCase = true) -> {
                    val range = parseByteRange(line.substringAfter(':', ""), lastRangeEnd)
                    pendingRange = range
                    if (range != null) lastRangeEnd = range.last + 1
                }
                line.startsWith("#EXT-X-MEDIA-SEQUENCE", ignoreCase = true) -> {
                    sequence = line.substringAfter(':', "").trim().toIntOrNull() ?: sequence
                }
                line.startsWith("#") -> Unit
                else -> {
                    segments += HlsSegment(
                        url = VideoSourceClassifier.resolveUrl(baseUrl, line),
                        byteRange = pendingRange,
                        sequence = sequence,
                        key = currentKey
                    )
                    sequence += 1
                    pendingRange = null
                }
            }
        }
        return HlsMediaPlaylist(
            segments = segments,
            initSegmentUrl = initUrl,
            initSegmentRange = initRange,
            initSegmentKey = initKey
        )
    }

    /**
     * `LENGTH[@OFFSET]` biçimindeki bayt aralığını `start until end` olarak döndürür.
     * `@OFFSET` yoksa [previousEnd]'den devam eder (HLS belirtimi böyle söylüyor).
     */
    fun parseByteRange(raw: String, previousEnd: Long): LongRange? {
        val value = raw.trim()
        if (value.isEmpty()) return null
        val parts = value.split('@')
        val length = parts[0].trim().toLongOrNull() ?: return null
        if (length <= 0L) return null
        val start = if (parts.size > 1) {
            parts[1].trim().toLongOrNull() ?: previousEnd
        } else {
            previousEnd
        }
        val safeStart = start.coerceAtLeast(0L)
        return safeStart until (safeStart + length)
    }

    /** `KEY=VALUE,KEY="quoted, value"` biçimindeki HLS öznitelik listesini çözer. */
    fun parseAttributes(raw: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        var index = 0
        val text = raw.trim()
        while (index < text.length) {
            val equals = text.indexOf('=', index)
            if (equals < 0) break
            val name = text.substring(index, equals).trim().trim(',').uppercase(Locale.ROOT)
            if (name.isEmpty()) {
                index = equals + 1
                continue
            }
            val valueStart = equals + 1
            val value: String
            if (valueStart < text.length && text[valueStart] == '"') {
                val closing = text.indexOf('"', valueStart + 1).let { if (it < 0) text.length else it }
                value = text.substring(valueStart + 1, closing)
                index = closing + 1
            } else {
                var end = text.indexOf(',', valueStart)
                if (end < 0) end = text.length
                value = text.substring(valueStart, end).trim()
                index = end
            }
            result[name] = value
            while (index < text.length && (text[index] == ',' || text[index] == ' ')) index++
        }
        return result
    }
}
