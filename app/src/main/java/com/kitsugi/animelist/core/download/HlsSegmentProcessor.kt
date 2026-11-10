package com.kitsugi.animelist.core.download

import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * HLS segment düzeyinde şifre çözme (`AES-128`), sahte resim başlığı temizleme (PNG/JPEG/GIF/JS
 * arkasına gizlenmiş MPEG-TS paketleri), Packed Audio (`ID3v2` + ADTS/AC-3/E-AC-3/MP3)
 * birleştirme ve MPEG-TS PAT/PMT akış denetimi yardımcıları.
 */
object HlsSegmentProcessor {

    const val TS_PACKET_SIZE = 188
    private const val TS_SYNC_BYTE: Byte = 0x47

    enum class SegmentFormat {
        MPEG_TS,
        FMP4,
        ADTS_AAC,
        AC3_EAC3,
        MP3,
        OTHER
    }

    data class SanitizedSegment(
        val bytes: ByteArray,
        val offset: Int,
        val length: Int,
        val format: SegmentFormat,
        /** Packed Audio segmentlerinde `com.apple.streaming.transportStreamTimestamp` etiketi varsa µs cinsinden PTS. */
        val id3TimestampUs: Long? = null
    ) {
        fun toByteArray(): ByteArray =
            if (offset == 0 && length == bytes.size) bytes else bytes.copyOfRange(offset, offset + length)
    }

    data class TsStreamInfo(
        val isMpegTs: Boolean,
        val hasVideo: Boolean,
        val videoStreamTypes: Set<Int>,
        val hasAudio: Boolean,
        val audioStreamTypes: Set<Int>,
        /** Ses var ve en az bir ses izi standart ADTS AAC (`0x0F`) dışında (ör. E-AC-3, AC-3, DTS, MP3). */
        val hasNonAacAudio: Boolean
    )

    private val ISO_BMFF_BOX_TYPES = setOf(
        "ftyp", "styp", "moof", "moov", "sidx", "emsg", "prft", "mdat", "free", "skip", "wide"
    )

    // ── AES-128 Şifre Çözme ───────────────────────────────────────────────────

    /**
     * RFC-8216 §5.2 uyumlu `AES-128` CBC segment şifre çözücüsü.
     */
    fun decryptAes128(
        encrypted: ByteArray,
        keyBytes: ByteArray,
        explicitIvHex: String?,
        sequenceNumber: Long
    ): ByteArray {
        if (encrypted.isEmpty()) return encrypted
        val normalizedKey = when {
            keyBytes.size == 16 -> keyBytes
            keyBytes.size > 16 -> keyBytes.copyOf(16)
            else -> throw IllegalArgumentException("Invalid AES-128 key length: ${keyBytes.size}")
        }
        val ivBytes = buildAesIv(explicitIvHex, sequenceNumber)
        val secretKey = SecretKeySpec(normalizedKey, "AES")
        val ivSpec = IvParameterSpec(ivBytes)

        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
            cipher.doFinal(encrypted)
        } catch (paddingEx: Exception) {
            if (encrypted.size % 16 == 0) {
                val rawCipher = Cipher.getInstance("AES/CBC/NoPadding")
                rawCipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
                rawCipher.doFinal(encrypted)
            } else {
                throw paddingEx
            }
        }
    }

    fun buildAesIv(explicitIvHex: String?, sequenceNumber: Long): ByteArray {
        if (!explicitIvHex.isNullOrBlank()) {
            val cleanHex = explicitIvHex.trim()
                .removePrefix("0x")
                .removePrefix("0X")
                .filter { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
            if (cleanHex.isNotEmpty()) {
                val padded = cleanHex.padStart(32, '0').takeLast(32)
                val out = ByteArray(16)
                for (i in 0 until 16) {
                    out[i] = padded.substring(i * 2, i * 2 + 2).toInt(16).toByte()
                }
                return out
            }
        }
        return ByteBuffer.allocate(16).putLong(8, sequenceNumber).array()
    }

    // ── Segment Temizleme (Sahte PNG/JPG Öneki & Packed Audio ID3) ────────────

    /**
     * Bir HLS segmentini analiz eder:
     * 1. ISO-BMFF (`fMP4` / `CMAF`) ise dokunmadan korur.
     * 2. Başta bir veya birden fazla `ID3v2` etiketi varsa PTS bilgisini okur ve ara segmentlerde
     *    ADTS/AC-3/MP3 akışını bozmaması için `ID3v2` başlığını soyar.
     * 3. Sahte PNG/JPEG/GIF/HTML/JS baytlarının arkasına gizlenmiş MPEG-TS paketlerini (`0x47` x3)
     *    bulup öneki temizler ve 188 bayt sınırına hizalar.
     */
    fun sanitizeSegment(rawBytes: ByteArray): SanitizedSegment {
        val total = rawBytes.size
        if (total == 0) {
            return SanitizedSegment(rawBytes, 0, 0, SegmentFormat.OTHER)
        }

        // 1) ISO-BMFF (fMP4 init veya media fragment)
        if (isIsoBmffSegment(rawBytes, total)) {
            return SanitizedSegment(rawBytes, 0, total, SegmentFormat.FMP4)
        }

        // 2) Baştaki ID3v2 etiket(ler)ini tara ve soy
        var cursor = 0
        var extractedPtsUs: Long? = null
        while (cursor + 10 <= total &&
            rawBytes[cursor] == 'I'.code.toByte() &&
            rawBytes[cursor + 1] == 'D'.code.toByte() &&
            rawBytes[cursor + 2] == '3'.code.toByte()
        ) {
            val major = rawBytes[cursor + 3].toInt() and 0xFF
            val minor = rawBytes[cursor + 4].toInt() and 0xFF
            val flags = rawBytes[cursor + 5].toInt() and 0xFF
            val b6 = rawBytes[cursor + 6].toInt() and 0xFF
            val b7 = rawBytes[cursor + 7].toInt() and 0xFF
            val b8 = rawBytes[cursor + 8].toInt() and 0xFF
            val b9 = rawBytes[cursor + 9].toInt() and 0xFF
            if (major == 0xFF || minor == 0xFF || b6 >= 0x80 || b7 >= 0x80 || b8 >= 0x80 || b9 >= 0x80) {
                break
            }
            val tagBodySize = (b6 shl 21) or (b7 shl 14) or (b8 shl 7) or b9
            val footerSize = if ((flags and 0x10) != 0) 10 else 0
            val fullTagSize = 10 + tagBodySize + footerSize
            if (cursor + fullTagSize > total) break

            if (extractedPtsUs == null) {
                extractedPtsUs = parseAppleTsTimestampUs(rawBytes, cursor + 10, tagBodySize)
            }
            cursor += fullTagSize
        }

        val remaining = total - cursor
        if (remaining <= 0) {
            return SanitizedSegment(rawBytes, 0, total, SegmentFormat.OTHER, extractedPtsUs)
        }

        // 3) Doğrudan MPEG-TS hizalı mı?
        if (isValidTsSyncAt(rawBytes, cursor, total)) {
            val alignedLen = alignTsLength(remaining)
            return SanitizedSegment(rawBytes, cursor, alignedLen, SegmentFormat.MPEG_TS, extractedPtsUs)
        }

        // 4) Packed Audio (ADTS AAC / AC-3 / E-AC-3 / MP3) kontrolü — ID3 soyulmuş ham ses
        detectPackedAudioFormat(rawBytes, cursor, total)?.let { audioFmt ->
            return SanitizedSegment(rawBytes, cursor, remaining, audioFmt, extractedPtsUs)
        }

        // 5) Sahte PNG/JPEG/GIF/WebP/JS başlığı arkasına gizlenmiş MPEG-TS senkron baytını ara
        val searchLimit = (cursor + 32_768).coerceAtMost(total - TS_PACKET_SIZE * 2 - 1)
        var pos = cursor + 1
        while (pos <= searchLimit) {
            if (rawBytes[pos] == TS_SYNC_BYTE && isValidThreePacketTsSync(rawBytes, pos, total)) {
                val tsLen = alignTsLength(total - pos)
                return SanitizedSegment(rawBytes, pos, tsLen, SegmentFormat.MPEG_TS, extractedPtsUs)
            }
            pos++
        }

        return SanitizedSegment(rawBytes, cursor, remaining, SegmentFormat.OTHER, extractedPtsUs)
    }

    private fun isIsoBmffSegment(bytes: ByteArray, length: Int): Boolean {
        if (length < 8) return false
        val boxType = String(bytes, 4, 4, Charsets.ISO_8859_1)
        return boxType in ISO_BMFF_BOX_TYPES
    }

    private fun isValidTsSyncAt(bytes: ByteArray, offset: Int, total: Int): Boolean {
        if (offset >= total || bytes[offset] != TS_SYNC_BYTE) return false
        if ((bytes[offset + 1].toInt() and 0x80) != 0) return false
        val rem = total - offset
        if (rem < TS_PACKET_SIZE * 2 + 1) {
            return rem >= TS_PACKET_SIZE && (rem % TS_PACKET_SIZE == 0 || bytes.getOrNull(offset + TS_PACKET_SIZE) == TS_SYNC_BYTE)
        }
        return isValidThreePacketTsSync(bytes, offset, total)
    }

    private fun isValidThreePacketTsSync(bytes: ByteArray, offset: Int, total: Int): Boolean {
        if (offset + TS_PACKET_SIZE * 2 + 1 >= total) return false
        val p0 = offset
        val p1 = offset + TS_PACKET_SIZE
        val p2 = offset + TS_PACKET_SIZE * 2
        return bytes[p0] == TS_SYNC_BYTE &&
            bytes[p1] == TS_SYNC_BYTE &&
            bytes[p2] == TS_SYNC_BYTE &&
            (bytes[p0 + 1].toInt() and 0x80) == 0 &&
            (bytes[p1 + 1].toInt() and 0x80) == 0 &&
            (bytes[p2 + 1].toInt() and 0x80) == 0
    }

    private fun alignTsLength(length: Int): Int {
        if (length < TS_PACKET_SIZE) return length
        val remainder = length % TS_PACKET_SIZE
        return if (remainder == 0) length else length - remainder
    }

    private fun detectPackedAudioFormat(bytes: ByteArray, offset: Int, total: Int): SegmentFormat? {
        if (offset + 4 > total) return null
        val b0 = bytes[offset].toInt() and 0xFF
        val b1 = bytes[offset + 1].toInt() and 0xFF

        // ADTS AAC: syncword 0xFFF, layer == 00 ((b1 & 0xF6) == 0xF0)
        if (b0 == 0xFF && (b1 and 0xF6) == 0xF0) {
            return SegmentFormat.ADTS_AAC
        }
        // AC-3 / E-AC-3 syncword: 0x0B 0x77
        if (b0 == 0x0B && b1 == 0x77) {
            return SegmentFormat.AC3_EAC3
        }
        // MP3 frame sync: 0xFF ve MPEG Audio layer != 00
        if (b0 == 0xFF && (b1 and 0xE0) == 0xE0 && (b1 and 0x06) != 0x00) {
            return SegmentFormat.MP3
        }
        return null
    }

    /**
     * ID3v2 gövdesinde `com.apple.streaming.transportStreamTimestamp\0` PRIV çerçevesini arar.
     * Bulursa 90kHz 33-bit PTS değerini mikrosaniyeye (`µs`) çevirir.
     */
    private fun parseAppleTsTimestampUs(bytes: ByteArray, bodyStart: Int, bodyLength: Int): Long? {
        val owner = "com.apple.streaming.transportStreamTimestamp\u0000".toByteArray(Charsets.ISO_8859_1)
        val end = (bodyStart + bodyLength).coerceAtMost(bytes.size)
        val maxSearch = end - owner.size - 8
        var i = bodyStart
        while (i <= maxSearch) {
            var match = true
            for (j in owner.indices) {
                if (bytes[i + j] != owner[j]) {
                    match = false
                    break
                }
            }
            if (match) {
                val tsOffset = i + owner.size
                var pts90k = 0L
                for (k in 0 until 8) {
                    pts90k = (pts90k shl 8) or (bytes[tsOffset + k].toLong() and 0xFFL)
                }
                pts90k = pts90k and 0x1FFFFFFFFL // 33-bit mask
                return (pts90k * 1_000_000L) / 90_000L
            }
            i++
        }
        return null
    }

    // ── MPEG-TS PAT / PMT Akış Denetçisi ──────────────────────────────────────

    private val VIDEO_STREAM_TYPES = setOf(0x01, 0x02, 0x10, 0x1B, 0x20, 0x24, 0x42, 0x51, 0xD1)
    private val DIRECT_AUDIO_STREAM_TYPES = setOf(
        0x03, 0x04, // MPEG-1/2 Audio (MP3)
        0x0F,       // ADTS AAC
        0x11,       // LATM AAC
        0x1C,       // MPEG-4 Audio
        0x7B, 0x82, 0x85, 0x86, // DTS
        0x80,       // LPCM
        0x81,       // AC-3
        0x84, 0x87  // E-AC-3
    )

    fun inspectTsFile(file: File, maxBytesToScan: Int = 1024 * 1024): TsStreamInfo {
        if (!file.isFile || file.length() < TS_PACKET_SIZE) {
            return TsStreamInfo(false, false, emptySet(), false, emptySet(), false)
        }
        val toRead = file.length().coerceAtMost(maxBytesToScan.toLong()).toInt()
        val buf = ByteArray(toRead)
        var offset = 0
        FileInputStream(file).use { fis ->
            while (offset < toRead) {
                val n = fis.read(buf, offset, toRead - offset)
                if (n <= 0) break
                offset += n
            }
        }
        return inspectTsBytes(buf, offset)
    }

    /**
     * MPEG-TS verisindeki PAT ve PMT tablolarını (ve gerçek paket PID sayılarını) tarayarak
     * video ve ses izlerinin varlığını ve codec türlerini kesin olarak belirler.
     */
    fun inspectTsBytes(bytes: ByteArray, length: Int = bytes.size): TsStreamInfo {
        val lim = length.coerceAtMost(bytes.size)
        if (lim < TS_PACKET_SIZE) {
            return TsStreamInfo(false, false, emptySet(), false, emptySet(), false)
        }

        // İlk senkron noktasını bul
        var start = -1
        val maxSyncSearch = (lim - TS_PACKET_SIZE).coerceAtMost(4096)
        for (i in 0..maxSyncSearch) {
            if (bytes[i] == TS_SYNC_BYTE && (i + TS_PACKET_SIZE >= lim || bytes[i + TS_PACKET_SIZE] == TS_SYNC_BYTE)) {
                start = i
                break
            }
        }
        if (start < 0) {
            return TsStreamInfo(false, false, emptySet(), false, emptySet(), false)
        }

        val pmtPids = HashSet<Int>()
        val declaredVideoPids = HashMap<Int, Int>() // pid -> streamType
        val declaredAudioPids = HashMap<Int, Int>() // pid -> effectiveStreamType
        val pidPayloadCounts = HashMap<Int, Int>()

        // 1. Geçiş: PAT -> PMT -> Elementary PID bildirimleri ve paket sayaçları
        var pos = start
        while (pos + TS_PACKET_SIZE <= lim) {
            if (bytes[pos] != TS_SYNC_BYTE) {
                pos++
                continue
            }
            val b1 = bytes[pos + 1].toInt() and 0xFF
            val b2 = bytes[pos + 2].toInt() and 0xFF
            val b3 = bytes[pos + 3].toInt() and 0xFF

            val tei = (b1 and 0x80) != 0
            if (!tei) {
                val pusi = (b1 and 0x40) != 0
                val pid = ((b1 and 0x1F) shl 8) or b2
                val afc = (b3 ushr 4) and 0x03
                val hasPayload = (afc and 0x01) != 0

                if (hasPayload) {
                    var payloadStart = pos + 4
                    if ((afc and 0x02) != 0) {
                        val afLen = bytes[pos + 4].toInt() and 0xFF
                        payloadStart += 1 + afLen
                    }
                    val packetEnd = pos + TS_PACKET_SIZE
                    if (payloadStart < packetEnd) {
                        pidPayloadCounts[pid] = (pidPayloadCounts[pid] ?: 0) + 1

                        if (pid == 0x0000 && pusi) {
                            parsePatSection(bytes, payloadStart, packetEnd, pmtPids)
                        } else if (pid in pmtPids && pusi) {
                            parsePmtSection(bytes, payloadStart, packetEnd, declaredVideoPids, declaredAudioPids)
                        }
                    }
                }
            }
            pos += TS_PACKET_SIZE
        }

        // Eğer ilk paketlerde PMT henüz PAT'tan önce geldiyse 2. kısa geçişle PMT'yi yakala
        if (pmtPids.isNotEmpty() && declaredVideoPids.isEmpty() && declaredAudioPids.isEmpty()) {
            pos = start
            while (pos + TS_PACKET_SIZE <= lim) {
                if (bytes[pos] == TS_SYNC_BYTE) {
                    val b1 = bytes[pos + 1].toInt() and 0xFF
                    val b2 = bytes[pos + 2].toInt() and 0xFF
                    val b3 = bytes[pos + 3].toInt() and 0xFF
                    val pusi = (b1 and 0x40) != 0
                    val pid = ((b1 and 0x1F) shl 8) or b2
                    val afc = (b3 ushr 4) and 0x03
                    if (pid in pmtPids && pusi && (afc and 0x01) != 0) {
                        var payloadStart = pos + 4
                        if ((afc and 0x02) != 0) {
                            payloadStart += 1 + (bytes[pos + 4].toInt() and 0xFF)
                        }
                        if (payloadStart < pos + TS_PACKET_SIZE) {
                            parsePmtSection(bytes, payloadStart, pos + TS_PACKET_SIZE, declaredVideoPids, declaredAudioPids)
                        }
                    }
                }
                pos += TS_PACKET_SIZE
            }
        }

        val activeVideoTypes = declaredVideoPids
            .filter { (pid, _) -> (pidPayloadCounts[pid] ?: 0) > 0 }
            .values
            .toSet()

        val activeAudioTypes = declaredAudioPids
            .filter { (pid, _) -> (pidPayloadCounts[pid] ?: 0) > 0 }
            .values
            .toSet()

        val hasNonAac = activeAudioTypes.any { it != 0x0F }

        return TsStreamInfo(
            isMpegTs = true,
            hasVideo = activeVideoTypes.isNotEmpty(),
            videoStreamTypes = activeVideoTypes,
            hasAudio = activeAudioTypes.isNotEmpty(),
            audioStreamTypes = activeAudioTypes,
            hasNonAacAudio = hasNonAac
        )
    }

    private fun parsePatSection(
        bytes: ByteArray,
        payloadStart: Int,
        packetEnd: Int,
        pmtPidsOut: MutableSet<Int>
    ) {
        val pointer = bytes[payloadStart].toInt() and 0xFF
        val secStart = payloadStart + 1 + pointer
        if (secStart + 8 > packetEnd) return
        val tableId = bytes[secStart].toInt() and 0xFF
        if (tableId != 0x00) return
        val secLen = (((bytes[secStart + 1].toInt() and 0x0F) shl 8) or (bytes[secStart + 2].toInt() and 0xFF))
        val secEnd = (secStart + 3 + secLen - 4).coerceAtMost(packetEnd) // CRC-32 hariç
        var idx = secStart + 8
        while (idx + 4 <= secEnd) {
            val programNum = ((bytes[idx].toInt() and 0xFF) shl 8) or (bytes[idx + 1].toInt() and 0xFF)
            val pid = ((bytes[idx + 2].toInt() and 0x1F) shl 8) or (bytes[idx + 3].toInt() and 0xFF)
            if (programNum != 0 && pid != 0) {
                pmtPidsOut += pid
            }
            idx += 4
        }
    }

    private fun parsePmtSection(
        bytes: ByteArray,
        payloadStart: Int,
        packetEnd: Int,
        videoPidsOut: MutableMap<Int, Int>,
        audioPidsOut: MutableMap<Int, Int>
    ) {
        val pointer = bytes[payloadStart].toInt() and 0xFF
        val secStart = payloadStart + 1 + pointer
        if (secStart + 12 > packetEnd) return
        val tableId = bytes[secStart].toInt() and 0xFF
        if (tableId != 0x02) return
        val secLen = (((bytes[secStart + 1].toInt() and 0x0F) shl 8) or (bytes[secStart + 2].toInt() and 0xFF))
        val secEnd = (secStart + 3 + secLen - 4).coerceAtMost(packetEnd)
        val progInfoLen = ((bytes[secStart + 10].toInt() and 0x0F) shl 8) or (bytes[secStart + 11].toInt() and 0xFF)
        var idx = secStart + 12 + progInfoLen

        while (idx + 5 <= secEnd) {
            val streamType = bytes[idx].toInt() and 0xFF
            val elemPid = ((bytes[idx + 1].toInt() and 0x1F) shl 8) or (bytes[idx + 2].toInt() and 0xFF)
            val esInfoLen = ((bytes[idx + 3].toInt() and 0x0F) shl 8) or (bytes[idx + 4].toInt() and 0xFF)
            val descStart = idx + 5
            val descEnd = (descStart + esInfoLen).coerceAtMost(secEnd)

            when {
                streamType in VIDEO_STREAM_TYPES -> {
                    videoPidsOut[elemPid] = streamType
                }
                streamType in DIRECT_AUDIO_STREAM_TYPES -> {
                    audioPidsOut[elemPid] = streamType
                }
                streamType == 0x06 -> {
                    detectPrivatePesAudioType(bytes, descStart, descEnd)?.let { resolvedAudioType ->
                        audioPidsOut[elemPid] = resolvedAudioType
                    }
                }
            }
            idx = descEnd
        }
    }

    private fun detectPrivatePesAudioType(bytes: ByteArray, descStart: Int, descEnd: Int): Int? {
        var d = descStart
        while (d + 2 <= descEnd) {
            val tag = bytes[d].toInt() and 0xFF
            val len = bytes[d + 1].toInt() and 0xFF
            val bodyStart = d + 2
            val bodyEnd = (bodyStart + len).coerceAtMost(descEnd)
            when (tag) {
                0x6A -> return 0x81 // DVB AC-3
                0x7A -> return 0x87 // DVB E-AC-3
                0x7B -> return 0x7B // DVB DTS
                0x7C -> return 0x0F // DVB AAC
                0x7F -> {
                    // DVB Extension descriptor: 0x80 -> Opus
                    if (bodyStart < bodyEnd && (bytes[bodyStart].toInt() and 0xFF) == 0x80) {
                        return 0x06
                    }
                }
                0x05 -> {
                    if (bodyEnd - bodyStart >= 4) {
                        val fourcc = String(bytes, bodyStart, 4, Charsets.ISO_8859_1)
                        when (fourcc) {
                            "AC-3" -> return 0x81
                            "EAC3", "EC-3" -> return 0x87
                            "DTS1", "DTS2", "DTS3", "DTSH" -> return 0x7B
                            "Opus" -> return 0x06
                            "BSSD" -> return 0x80
                        }
                    }
                }
            }
            d = bodyEnd
        }
        return null
    }

    // ── Kapsayıcı / Dosya Uzantısı Tespiti ────────────────────────────────────

    fun detectContainerExtension(file: File, fallbackExtension: String = "mp4"): String {
        if (!file.isFile || file.length() < 4) return fallbackExtension
        val head = ByteArray(64.coerceAtMost(file.length().toInt()))
        val read = FileInputStream(file).use { it.read(head) }
        if (read <= 0) return fallbackExtension
        return detectContainerExtensionFromBytes(head, read, fallbackExtension)
    }

    fun detectContainerExtensionFromBytes(
        bytes: ByteArray,
        length: Int = bytes.size,
        fallbackExtension: String = "mp4"
    ): String {
        val lim = length.coerceAtMost(bytes.size)
        if (lim < 4) return fallbackExtension

        // Matroska / WebM EBML header: 1A 45 DF A3
        if ((bytes[0].toInt() and 0xFF) == 0x1A &&
            (bytes[1].toInt() and 0xFF) == 0x45 &&
            (bytes[2].toInt() and 0xFF) == 0xDF &&
            (bytes[3].toInt() and 0xFF) == 0xA3
        ) {
            val ascii = String(bytes, 0, lim, Charsets.ISO_8859_1).lowercase(Locale.ROOT)
            return if (ascii.contains("webm")) "webm" else "mkv"
        }

        // ISO-BMFF (MP4 / M4A)
        if (lim >= 8) {
            val boxType = String(bytes, 4, 4, Charsets.ISO_8859_1)
            if (boxType in ISO_BMFF_BOX_TYPES) {
                if (boxType == "ftyp" && lim >= 12) {
                    val majorBrand = String(bytes, 8, 4, Charsets.ISO_8859_1).lowercase(Locale.ROOT)
                    if (majorBrand == "m4a ") return "m4a"
                }
                return if (fallbackExtension == "m4a") "m4a" else "mp4"
            }
        }

        // Ogg
        if (bytes[0] == 'O'.code.toByte() && bytes[1] == 'g'.code.toByte() &&
            bytes[2] == 'g'.code.toByte() && bytes[3] == 'S'.code.toByte()
        ) {
            return "ogg"
        }

        // FLAC
        if (bytes[0] == 'f'.code.toByte() && bytes[1] == 'L'.code.toByte() &&
            bytes[2] == 'a'.code.toByte() && bytes[3] == 'C'.code.toByte()
        ) {
            return "flac"
        }

        // RIFF AVI
        if (lim >= 12 &&
            bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'A'.code.toByte() && bytes[9] == 'V'.code.toByte() &&
            bytes[10] == 'I'.code.toByte()
        ) {
            return "avi"
        }

        // MPEG-TS
        if (bytes[0] == TS_SYNC_BYTE) {
            return "ts"
        }

        // Packed Audio
        detectPackedAudioFormat(bytes, 0, lim)?.let { fmt ->
            return when (fmt) {
                SegmentFormat.ADTS_AAC -> "aac"
                SegmentFormat.AC3_EAC3 -> "eac3"
                SegmentFormat.MP3 -> "mp3"
                else -> fallbackExtension
            }
        }

        // ID3 ile başlayan MP3 / AAC
        if (bytes[0] == 'I'.code.toByte() && bytes[1] == 'D'.code.toByte() && bytes[2] == '3'.code.toByte()) {
            return if (fallbackExtension in setOf("aac", "m4a", "eac3", "ac3")) fallbackExtension else "mp3"
        }

        return fallbackExtension
    }
}
