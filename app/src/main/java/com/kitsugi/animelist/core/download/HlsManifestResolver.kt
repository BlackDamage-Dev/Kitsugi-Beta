package com.kitsugi.animelist.core.download

import com.kitsugi.animelist.data.model.ExternalAudioTrack
import java.net.URI
import java.nio.ByteBuffer
import java.util.Locale
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * RFC 8216 uyumlu HLS Master & Media Playlist ayrıştırıcısı ve AES-128 çözücüsü.
 *
 * Saf JVM kodu olduğu için birim testlerinde doğrudan çalıştırılabilir.
 */
object HlsManifestResolver {

    data class ByteRange(val length: Long, val offset: Long) {
        fun toHttpRangeHeader(): String = "bytes=$offset-${offset + length - 1}"
    }

    data class EncryptionInfo(
        val method: String,
        val keyUrl: String?,
        val explicitIv: ByteArray?
    ) {
        val isEncrypted: Boolean
            get() = !method.equals("NONE", ignoreCase = true) && method.isNotBlank()

        val isAes128: Boolean
            get() = method.equals("AES-128", ignoreCase = true)

        fun resolveIv(mediaSequence: Long): ByteArray {
            if (explicitIv != null && explicitIv.size == 16) return explicitIv
            val buf = ByteBuffer.allocate(16)
            buf.putLong(0L)
            buf.putLong(mediaSequence)
            return buf.array()
        }
    }

    data class InitSegment(
        val url: String,
        val byteRange: ByteRange? = null,
        val encryption: EncryptionInfo? = null,
        val mediaSequence: Long = 0L
    )

    data class MediaSegment(
        val url: String,
        val durationSec: Double,
        val sequenceNumber: Long,
        val byteRange: ByteRange? = null,
        val initSegment: InitSegment? = null,
        val encryption: EncryptionInfo? = null
    )

    data class VariantStream(
        val url: String,
        val bandwidth: Long,
        val averageBandwidth: Long,
        val width: Int,
        val height: Int,
        val codecs: String,
        val audioGroupId: String?,
        val subtitlesGroupId: String?
    )

    data class AudioRendition(
        val groupId: String,
        val name: String,
        val language: String,
        val uri: String?,
        val isDefault: Boolean,
        val autoSelect: Boolean,
        val channels: Int,
        val index: Int
    ) {
        /** RFC 8216 §4.3.4.1: URI varsa ses ayrı bir akıştadır; yoksa video varyantının içine gömülüdür. */
        val isSeparateStream: Boolean
            get() = !uri.isNullOrBlank()

        val normalizedLang: String
            get() = ExternalAudioTrack.normalizeLangCode(
                language.ifBlank { ExternalAudioTrack.inferLanguageFromText("$name ${uri.orEmpty()}") }
            )

        val trackKey: String
            get() = "${normalizedLang.ifBlank { "und" }}_$index"
    }

    sealed class ParsedPlaylist {
        data class Master(
            val baseUrl: String,
            val variants: List<VariantStream>,
            val audioRenditions: List<AudioRendition>
        ) : ParsedPlaylist()

        data class Media(
            val baseUrl: String,
            val mediaSequence: Long,
            val segments: List<MediaSegment>,
            val isEndList: Boolean
        ) : ParsedPlaylist()
    }

    fun parse(manifestUrl: String, body: String): ParsedPlaylist? {
        val rawLines = body.lineSequence()
            .map { it.trim().removePrefix("\uFEFF").trim() }
            .filter { it.isNotEmpty() }
            .toList()
        if (rawLines.isEmpty()) return null

        val hasExtM3u = rawLines.first().startsWith("#EXTM3U", ignoreCase = true)
        val hasStreamInf = rawLines.any { it.startsWith("#EXT-X-STREAM-INF", ignoreCase = true) }
        val hasMediaTags = rawLines.any {
            it.startsWith("#EXTINF", ignoreCase = true) ||
                it.startsWith("#EXT-X-TARGETDURATION", ignoreCase = true) ||
                it.startsWith("#EXT-X-MAP", ignoreCase = true) ||
                it.startsWith("#EXT-X-MEDIA-SEQUENCE", ignoreCase = true)
        }

        if (hasStreamInf || rawLines.any { it.startsWith("#EXT-X-MEDIA:", ignoreCase = true) }) {
            return parseMaster(manifestUrl, rawLines)
        }
        if (hasMediaTags) {
            return parseMedia(manifestUrl, rawLines)
        }

        // Bazı CDN'ler (CloseLoad / playmix) çıplak alt .m3u8 satırları döndürebilir
        if (hasExtM3u || rawLines.all { !it.startsWith("<") }) {
            val bareVariants = rawLines.filter { !it.startsWith("#") }.mapNotNull { line ->
                val resolved = resolveUrl(manifestUrl, line)
                if (resolved.contains(".m3u8", ignoreCase = true) ||
                    resolved.contains("master.txt", ignoreCase = true) ||
                    resolved.contains("index", ignoreCase = true)
                ) {
                    val guessedHeight = Regex("""(\d{3,4})p""").find(line.lowercase(Locale.ROOT))
                        ?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                    VariantStream(
                        url = resolved,
                        bandwidth = guessedHeight * 2500L,
                        averageBandwidth = 0L,
                        width = 0,
                        height = guessedHeight,
                        codecs = "",
                        audioGroupId = null,
                        subtitlesGroupId = null
                    )
                } else null
            }
            if (bareVariants.isNotEmpty()) {
                return ParsedPlaylist.Master(
                    baseUrl = manifestUrl,
                    variants = bareVariants,
                    audioRenditions = emptyList()
                )
            }
        }
        return null
    }

    private fun parseMaster(manifestUrl: String, lines: List<String>): ParsedPlaylist.Master {
        val variants = ArrayList<VariantStream>()
        val audios = ArrayList<AudioRendition>()
        var audioIdx = 0

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            when {
                line.startsWith("#EXT-X-MEDIA:", ignoreCase = true) -> {
                    val attrs = parseAttributes(line)
                    if (attrs["TYPE"].equals("AUDIO", ignoreCase = true)) {
                        val rawUri = unquote(attrs["URI"])?.takeIf { it.isNotBlank() }
                        val resolvedUri = rawUri?.let { resolveUrl(manifestUrl, it) }
                        val channelsStr = unquote(attrs["CHANNELS"])?.substringBefore('/')
                        audios.add(
                            AudioRendition(
                                groupId = unquote(attrs["GROUP-ID"]).orEmpty(),
                                name = unquote(attrs["NAME"]).orEmpty(),
                                language = unquote(attrs["LANGUAGE"])
                                    ?: unquote(attrs["ASSOC-LANGUAGE"]).orEmpty(),
                                uri = resolvedUri,
                                isDefault = unquote(attrs["DEFAULT"]).equals("YES", ignoreCase = true),
                                autoSelect = unquote(attrs["AUTOSELECT"]).equals("YES", ignoreCase = true),
                                channels = channelsStr?.toIntOrNull() ?: 0,
                                index = audioIdx++
                            )
                        )
                    }
                }

                line.startsWith("#EXT-X-STREAM-INF:", ignoreCase = true) -> {
                    val attrs = parseAttributes(line)
                    var nextIdx = i + 1
                    while (nextIdx < lines.size && lines[nextIdx].startsWith("#")) {
                        nextIdx++
                    }
                    if (nextIdx < lines.size) {
                        val variantUrl = resolveUrl(manifestUrl, lines[nextIdx])
                        val (w, h) = parseResolution(unquote(attrs["RESOLUTION"]).orEmpty())
                        val bw = unquote(attrs["BANDWIDTH"])?.toLongOrNull() ?: 0L
                        val avgBw = unquote(attrs["AVERAGE-BANDWIDTH"])?.toLongOrNull() ?: 0L
                        variants.add(
                            VariantStream(
                                url = variantUrl,
                                bandwidth = bw,
                                averageBandwidth = avgBw,
                                width = w,
                                height = h,
                                codecs = unquote(attrs["CODECS"]).orEmpty(),
                                audioGroupId = unquote(attrs["AUDIO"]),
                                subtitlesGroupId = unquote(attrs["SUBTITLES"])
                            )
                        )
                        i = nextIdx
                    }
                }
            }
            i++
        }

        return ParsedPlaylist.Master(
            baseUrl = manifestUrl,
            variants = variants,
            audioRenditions = audios
        )
    }

    private fun parseMedia(manifestUrl: String, lines: List<String>): ParsedPlaylist.Media {
        var mediaSequence = 0L
        var isEndList = false
        var currentEncryption: EncryptionInfo? = null
        var currentInit: InitSegment? = null
        var pendingDuration = 0.0
        var pendingByteRange: ByteRange? = null
        val lastEndOffsetByUrl = HashMap<String, Long>()
        var lastInitEndOffset = 0L

        val segments = ArrayList<MediaSegment>()
        var segIndex = 0L

        for (line in lines) {
            when {
                line.startsWith("#EXT-X-MEDIA-SEQUENCE:", ignoreCase = true) -> {
                    mediaSequence = line.substringAfter(':').trim().toLongOrNull() ?: 0L
                }

                line.startsWith("#EXT-X-ENDLIST", ignoreCase = true) -> {
                    isEndList = true
                }

                line.startsWith("#EXT-X-KEY:", ignoreCase = true) -> {
                    val attrs = parseAttributes(line)
                    val method = unquote(attrs["METHOD"]).orEmpty()
                    currentEncryption = if (method.equals("NONE", ignoreCase = true) || method.isBlank()) {
                        null
                    } else {
                        val keyUri = unquote(attrs["URI"])?.takeIf { it.isNotBlank() }
                            ?.let { resolveUrl(manifestUrl, it) }
                        val iv = parseHexIv(unquote(attrs["IV"]))
                        EncryptionInfo(method = method, keyUrl = keyUri, explicitIv = iv)
                    }
                }

                line.startsWith("#EXT-X-MAP:", ignoreCase = true) -> {
                    val attrs = parseAttributes(line)
                    val mapUri = unquote(attrs["URI"])?.takeIf { it.isNotBlank() }
                        ?.let { resolveUrl(manifestUrl, it) }
                    if (mapUri != null) {
                        val br = parseByteRange(unquote(attrs["BYTERANGE"]), lastInitEndOffset)
                        if (br != null) {
                            lastInitEndOffset = br.offset + br.length
                        }
                        currentInit = InitSegment(
                            url = mapUri,
                            byteRange = br,
                            encryption = currentEncryption,
                            mediaSequence = mediaSequence + segIndex
                        )
                    }
                }

                line.startsWith("#EXTINF:", ignoreCase = true) -> {
                    val durPart = line.substringAfter(':').substringBefore(',').trim()
                    pendingDuration = durPart.toDoubleOrNull() ?: 0.0
                }

                line.startsWith("#EXT-X-BYTERANGE:", ignoreCase = true) -> {
                    val rawRange = line.substringAfter(':').trim()
                    // Sonraki satırdaki URL ile eşleştiğinde nihai offset çözülür; şimdilik ham tutalım
                    pendingByteRange = parseByteRange(rawRange, -1L)
                }

                !line.startsWith("#") -> {
                    val segUrl = resolveUrl(manifestUrl, line)
                    val resolvedRange = pendingByteRange?.let { pr ->
                        val offset = if (pr.offset >= 0L) {
                            pr.offset
                        } else {
                            lastEndOffsetByUrl[segUrl] ?: 0L
                        }
                        lastEndOffsetByUrl[segUrl] = offset + pr.length
                        ByteRange(length = pr.length, offset = offset)
                    }
                    val seq = mediaSequence + segIndex
                    segments.add(
                        MediaSegment(
                            url = segUrl,
                            durationSec = pendingDuration,
                            sequenceNumber = seq,
                            byteRange = resolvedRange,
                            initSegment = currentInit,
                            encryption = currentEncryption
                        )
                    )
                    segIndex++
                    pendingDuration = 0.0
                    pendingByteRange = null
                }
            }
        }

        return ParsedPlaylist.Media(
            baseUrl = manifestUrl,
            mediaSequence = mediaSequence,
            segments = segments,
            isEndList = isEndList
        )
    }

    fun selectBestVariant(
        master: ParsedPlaylist.Master,
        preferredMaxHeight: Int? = null,
        codecFilter: (String?) -> Boolean = DeviceCodecSupport::isCodecListSupported
    ): VariantStream? {
        if (master.variants.isEmpty()) return null
        val supportedVariants = master.variants.filter { codecFilter(it.codecs) }
        val pool = if (supportedVariants.isNotEmpty()) supportedVariants else master.variants

        val comparator = compareBy<VariantStream>(
            { it.height },
            { if (it.averageBandwidth > 0) it.averageBandwidth else it.bandwidth },
            { it.width }
        )
        if (preferredMaxHeight != null && preferredMaxHeight > 0) {
            val capped = pool.filter { it.height in 1..preferredMaxHeight }
            if (capped.isNotEmpty()) {
                return capped.maxWithOrNull(comparator)
            }
        }
        return pool.maxWithOrNull(comparator)
    }

    fun audioRenditionsForVariant(
        master: ParsedPlaylist.Master,
        variant: VariantStream?
    ): List<AudioRendition> {
        if (master.audioRenditions.isEmpty()) return emptyList()
        val groupId = variant?.audioGroupId
        if (!groupId.isNullOrBlank()) {
            val matched = master.audioRenditions.filter { it.groupId.equals(groupId, ignoreCase = true) }
            if (matched.isNotEmpty()) return matched
        }
        return master.audioRenditions
    }

    fun selectPreferredAudioRendition(
        renditions: List<AudioRendition>,
        preferredLanguages: List<String> = listOf("tr", "ja", "en"),
        requestedKey: String? = null,
        requestedIndex: Int = -1,
        requestedLang: String? = null,
        requestedLabel: String? = null
    ): AudioRendition? {
        if (renditions.isEmpty()) return null

        if (!requestedKey.isNullOrBlank() && requestedKey != "default") {
            renditions.firstOrNull { it.trackKey.equals(requestedKey, ignoreCase = true) }
                ?.let { return it }
        }
        if (requestedIndex in renditions.indices) {
            return renditions[requestedIndex]
        }
        if (!requestedLabel.isNullOrBlank()) {
            val cleanReq = requestedLabel.trim()
            renditions.firstOrNull {
                it.name.equals(cleanReq, ignoreCase = true) ||
                    cleanReq.contains(it.name, ignoreCase = true) && it.name.length >= 3
            }?.let { return it }
        }
        val normReqLang = ExternalAudioTrack.normalizeLangCode(requestedLang)
        if (normReqLang.isNotBlank()) {
            renditions.firstOrNull { it.normalizedLang == normReqLang }?.let { return it }
        }

        for (pref in preferredLanguages) {
            val normPref = ExternalAudioTrack.normalizeLangCode(pref)
            if (normPref.isBlank()) continue
            renditions.firstOrNull { it.normalizedLang == normPref }?.let { return it }
        }

        return renditions.firstOrNull { it.isDefault }
            ?: renditions.firstOrNull { it.autoSelect }
            ?: renditions.first()
    }

    /**
     * AES-128-CBC ile şifrelenmiş HLS segmentini çözer.
     * Standart PKCS#7 dolgulu segmentlerin yanı sıra dolgusuz (16-bayt hizalı) segmentleri de destekler.
     */
    fun decryptAes128(cipherBytes: ByteArray, keyBytes: ByteArray, ivBytes: ByteArray): ByteArray {
        require(keyBytes.size == 16) { "AES-128 anahtarı 16 bayt olmalı (gelen=${keyBytes.size})" }
        val keySpec = SecretKeySpec(keyBytes, "AES")
        val ivSpec = IvParameterSpec(if (ivBytes.size == 16) ivBytes else ivBytes.copyOf(16))
        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            cipher.doFinal(cipherBytes)
        } catch (_: BadPaddingException) {
            if (cipherBytes.size % 16 == 0) {
                val rawCipher = Cipher.getInstance("AES/CBC/NoPadding")
                rawCipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
                rawCipher.doFinal(cipherBytes)
            } else {
                throw IllegalStateException("AES-128 segment dolgusu bozuk (${cipherBytes.size} bayt)")
            }
        }
    }

    internal fun parseAttributes(line: String): Map<String, String> {
        val colonIdx = line.indexOf(':')
        if (colonIdx < 0) return emptyMap()
        val attrs = LinkedHashMap<String, String>()
        val key = StringBuilder()
        val value = StringBuilder()
        var inKey = true
        var inQuotes = false
        for (ch in line.substring(colonIdx + 1)) {
            if (inKey) {
                if (ch == '=') {
                    inKey = false
                } else {
                    key.append(ch)
                }
            } else {
                when {
                    ch == '"' -> {
                        inQuotes = !inQuotes
                        value.append(ch)
                    }
                    ch == ',' && !inQuotes -> {
                        val k = key.toString().trim().uppercase(Locale.ROOT)
                        if (k.isNotEmpty()) attrs[k] = value.toString().trim()
                        key.clear()
                        value.clear()
                        inKey = true
                    }
                    else -> value.append(ch)
                }
            }
        }
        val lastKey = key.toString().trim().uppercase(Locale.ROOT)
        if (lastKey.isNotEmpty()) {
            attrs[lastKey] = value.toString().trim()
        }
        return attrs
    }

    private fun unquote(raw: String?): String? =
        raw?.trim()?.removeSurrounding("\"")

    private fun parseResolution(raw: String): Pair<Int, Int> {
        val parts = raw.lowercase(Locale.ROOT).split('x', limit = 2)
        if (parts.size != 2) return 0 to 0
        return (parts[0].toIntOrNull() ?: 0) to (parts[1].toIntOrNull() ?: 0)
    }

    private fun parseByteRange(raw: String?, implicitOffset: Long): ByteRange? {
        if (raw.isNullOrBlank()) return null
        val clean = raw.trim().removeSurrounding("\"")
        val lengthStr = clean.substringBefore('@')
        val length = lengthStr.toLongOrNull() ?: return null
        val offset = if ('@' in clean) {
            clean.substringAfter('@').toLongOrNull() ?: return null
        } else {
            implicitOffset
        }
        return ByteRange(length = length, offset = offset)
    }

    internal fun parseHexIv(raw: String?): ByteArray? {
        if (raw.isNullOrBlank()) return null
        var hex = raw.trim().removePrefix("0x").removePrefix("0X")
        if (hex.isEmpty()) return null
        if (hex.length % 2 != 0) hex = "0$hex"
        if (hex.length < 32) hex = hex.padStart(32, '0')
        if (hex.length > 32) hex = hex.takeLast(32)
        return try {
            ByteArray(16) { idx ->
                hex.substring(idx * 2, idx * 2 + 2).toInt(16).toByte()
            }
        } catch (_: Exception) {
            null
        }
    }

    fun resolveUrl(baseUrl: String, childUrl: String): String {
        val trimmed = childUrl.trim()
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return trimmed
        }
        return runCatching {
            URI(baseUrl).resolve(trimmed).toString()
        }.getOrElse { trimmed }
    }
}
