package com.kitsugi.animelist.core.download

import java.util.Locale

/**
 * Akış ve segment biçimlerini URL uzantısına körü körüne güvenmek yerine
 * `Content-Type` + ilk bayt imzalarıyla (magic bytes) tespit eder.
 *
 * Desteklediği özel durumlar:
 * - `.txt`, `.png`, `.jpg` veya uzantısız URL arkasındaki `#EXTM3U` HLS manifestleri
 * - Sahte PNG / JPEG / GIF / BMP / ICO başlığı eklenmiş MPEG-TS segmentleri (`0x47` 188-bayt senkron)
 * - RFC 8216 Packed Audio segmentlerinin başındaki `ID3` zaman damgası etiketleri (`ADTS AAC`, `MP3`, `AC-3`, `E-AC-3`)
 * - Sunucunun medya yerine döndürdüğü Cloudflare / 403 / embed HTML hata sayfaları
 */
object StreamFormatSniffer {

    enum class DetectedFormat {
        HLS_MANIFEST,
        DASH_MANIFEST,
        HTML_PAGE,
        MP4,
        M4A,
        FMP4_SEGMENT,
        MKV,
        WEBM,
        MPEG_TS,
        FLV,
        AVI,
        ADTS_AAC,
        MP3,
        AC3,
        EAC3,
        OPUS_OGG,
        VORBIS_OGG,
        FLAC,
        WAV,
        DTS,
        UNKNOWN
    }

    fun sniff(
        bytes: ByteArray,
        length: Int = bytes.size,
        contentType: String? = null,
        url: String? = null
    ): DetectedFormat {
        val ct = contentType?.lowercase(Locale.ROOT)?.substringBefore(';')?.trim().orEmpty()
        val limit = length.coerceAtMost(bytes.size)

        if (limit > 0) {
            val headText = decodeAsciiHead(bytes, limit.coerceAtMost(1024))
            val trimmed = headText.trimStart('\uFEFF', ' ', '\t', '\r', '\n')
            if (trimmed.startsWith("#EXTM3U", ignoreCase = true) ||
                trimmed.contains("\n#EXT-X-STREAM-INF", ignoreCase = true) ||
                trimmed.contains("\n#EXTINF:", ignoreCase = true)
            ) {
                return DetectedFormat.HLS_MANIFEST
            }
            if (trimmed.startsWith("<MPD", ignoreCase = true) ||
                (trimmed.startsWith("<?xml", ignoreCase = true) && headText.contains("<MPD", ignoreCase = true))
            ) {
                return DetectedFormat.DASH_MANIFEST
            }
            if (trimmed.startsWith("<!DOCTYPE html", ignoreCase = true) ||
                trimmed.startsWith("<html", ignoreCase = true) ||
                trimmed.startsWith("<script", ignoreCase = true)
            ) {
                return DetectedFormat.HTML_PAGE
            }
        }

        // EBML (Matroska / WebM): 1A 45 DF A3
        if (limit >= 4 &&
            bytes[0] == 0x1A.toByte() &&
            bytes[1] == 0x45.toByte() &&
            bytes[2] == 0xDF.toByte() &&
            bytes[3] == 0xA3.toByte()
        ) {
            val ebmlHeader = decodeAsciiHead(bytes, limit.coerceAtMost(128)).lowercase(Locale.ROOT)
            return if (ebmlHeader.contains("webm")) DetectedFormat.WEBM else DetectedFormat.MKV
        }

        // ISO BMFF (MP4 / M4A / fMP4): [4-byte size][4-byte box type]
        if (limit >= 12) {
            val boxType = String(bytes, 4, 4, Charsets.ISO_8859_1)
            if (boxType == "ftyp" || boxType == "styp") {
                val majorBrand = String(bytes, 8, 4, Charsets.ISO_8859_1)
                if (boxType == "styp") return DetectedFormat.FMP4_SEGMENT
                if (majorBrand.equals("M4A ", ignoreCase = true) ||
                    majorBrand.equals("M4B ", ignoreCase = true) ||
                    ct.startsWith("audio/")
                ) {
                    return DetectedFormat.M4A
                }
                // ftyp içinden moof var mı kontrol et (fMP4 init+segment birleşimi olabilir ama yine MP4 uyumludur)
                return DetectedFormat.MP4
            }
            if (boxType == "moov" || boxType == "mdat" || boxType == "wide" || boxType == "free") {
                return if (ct.startsWith("audio/")) DetectedFormat.M4A else DetectedFormat.MP4
            }
            if (boxType == "moof" || boxType == "sidx" || boxType == "emsg") {
                return DetectedFormat.FMP4_SEGMENT
            }
        }

        // Ogg (Opus / Vorbis / Ogg-FLAC): "OggS"
        if (limit >= 4 &&
            bytes[0] == 'O'.code.toByte() &&
            bytes[1] == 'g'.code.toByte() &&
            bytes[2] == 'g'.code.toByte() &&
            bytes[3] == 'S'.code.toByte()
        ) {
            val oggHead = decodeAsciiHead(bytes, limit.coerceAtMost(128))
            return if (oggHead.contains("OpusHead")) DetectedFormat.OPUS_OGG else DetectedFormat.VORBIS_OGG
        }

        // RIFF WAVE / AVI: "RIFF"....("WAVE" | "AVI ")
        if (limit >= 12 &&
            bytes[0] == 'R'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() &&
            bytes[3] == 'F'.code.toByte()
        ) {
            if (bytes[8] == 'W'.code.toByte() &&
                bytes[9] == 'A'.code.toByte() &&
                bytes[10] == 'V'.code.toByte() &&
                bytes[11] == 'E'.code.toByte()
            ) {
                return DetectedFormat.WAV
            }
            if (bytes[8] == 'A'.code.toByte() &&
                bytes[9] == 'V'.code.toByte() &&
                bytes[10] == 'I'.code.toByte()
            ) {
                return DetectedFormat.AVI
            }
        }

        // Flash Video (FLV): "FLV\x01"
        if (limit >= 4 &&
            bytes[0] == 'F'.code.toByte() &&
            bytes[1] == 'L'.code.toByte() &&
            bytes[2] == 'V'.code.toByte() &&
            bytes[3] == 0x01.toByte()
        ) {
            return DetectedFormat.FLV
        }

        // ID3 etiketi sonrasındaki temel ses akışlarını incele (Packed Audio HLS / MP3 / FLAC / AAC / AC3 / EAC3)
        val payloadOffset = skipLeadingId3Tags(bytes, limit)
        if (payloadOffset < limit) {
            val rem = limit - payloadOffset
            // FLAC: "fLaC"
            if (rem >= 4 &&
                bytes[payloadOffset] == 'f'.code.toByte() &&
                bytes[payloadOffset + 1] == 'L'.code.toByte() &&
                bytes[payloadOffset + 2] == 'a'.code.toByte() &&
                bytes[payloadOffset + 3] == 'C'.code.toByte()
            ) {
                return DetectedFormat.FLAC
            }

            // AC-3 / E-AC-3 syncword: 0x0B 0x77
            if (rem >= 6 &&
                bytes[payloadOffset] == 0x0B.toByte() &&
                bytes[payloadOffset + 1] == 0x77.toByte()
            ) {
                val bsid = (bytes[payloadOffset + 5].toInt() ushr 3) and 0x1F
                return if (bsid in 11..16) DetectedFormat.EAC3 else DetectedFormat.AC3
            }

            // DTS Coherent Acoustics syncword: 7F FE 80 01
            if (rem >= 4 &&
                bytes[payloadOffset] == 0x7F.toByte() &&
                bytes[payloadOffset + 1] == 0xFE.toByte() &&
                bytes[payloadOffset + 2] == 0x80.toByte() &&
                bytes[payloadOffset + 3] == 0x01.toByte()
            ) {
                return DetectedFormat.DTS
            }

            // ADTS AAC vs MP3 frame sync (0xFF ...)
            if (rem >= 4 && (bytes[payloadOffset].toInt() and 0xFF) == 0xFF) {
                val b1 = bytes[payloadOffset + 1].toInt() and 0xFF
                // ADTS: syncword 12-bit 0xFFF, layer bits (bits 2..1) == 00 -> (b1 & 0xF6) == 0xF0
                if ((b1 and 0xF6) == 0xF0) {
                    return DetectedFormat.ADTS_AAC
                }
                // MPEG Audio (MP3): syncword 11-bit 0x7FF, version != 01, layer != 00
                if ((b1 and 0xE0) == 0xE0) {
                    val version = (b1 ushr 3) and 0x03
                    val layer = (b1 ushr 1) and 0x03
                    if (version != 1 && layer != 0) {
                        return DetectedFormat.MP3
                    }
                }
            }
        }

        // MPEG-TS (doğrudan 0x47 veya sahte PNG/JPEG/GIF/BMP başlığı arkasına gizlenmiş 188-bayt TS paketleri)
        val tsOffset = findMpegTsSyncOffset(bytes, limit)
        if (tsOffset >= 0) {
            return DetectedFormat.MPEG_TS
        }

        // Content-Type veya URL ipucu ile son geri dönüş
        return when {
            ct.contains("mpegurl") -> DetectedFormat.HLS_MANIFEST
            ct.contains("dash+xml") -> DetectedFormat.DASH_MANIFEST
            ct == "audio/mp4" || ct == "audio/x-m4a" -> DetectedFormat.M4A
            ct == "audio/aac" || ct == "audio/aacp" -> DetectedFormat.ADTS_AAC
            ct == "audio/mpeg" || ct == "audio/mp3" -> DetectedFormat.MP3
            ct == "audio/eac3" || ct == "audio/ec-3" -> DetectedFormat.EAC3
            ct == "audio/ac3" -> DetectedFormat.AC3
            ct == "audio/opus" -> DetectedFormat.OPUS_OGG
            ct == "audio/ogg" || ct == "application/ogg" -> DetectedFormat.VORBIS_OGG
            ct == "audio/flac" || ct == "audio/x-flac" -> DetectedFormat.FLAC
            ct == "audio/wav" || ct == "audio/x-wav" -> DetectedFormat.WAV
            url != null -> inferFromUrl(url)
            else -> DetectedFormat.UNKNOWN
        }
    }

    fun inferFromUrl(url: String): DetectedFormat {
        val clean = url.substringBefore('#').substringBefore('?').lowercase(Locale.ROOT)
        return when {
            clean.endsWith(".mpd") -> DetectedFormat.DASH_MANIFEST
            com.kitsugi.animelist.core.player.PlayerMediaSourceFactory.isHlsUrl(url) -> DetectedFormat.HLS_MANIFEST
            clean.endsWith(".m4a") -> DetectedFormat.M4A
            clean.endsWith(".aac") -> DetectedFormat.ADTS_AAC
            clean.endsWith(".mp3") -> DetectedFormat.MP3
            clean.endsWith(".eac3") || clean.endsWith(".ec3") -> DetectedFormat.EAC3
            clean.endsWith(".ac3") -> DetectedFormat.AC3
            clean.endsWith(".opus") -> DetectedFormat.OPUS_OGG
            clean.endsWith(".ogg") || clean.endsWith(".oga") -> DetectedFormat.VORBIS_OGG
            clean.endsWith(".flac") -> DetectedFormat.FLAC
            clean.endsWith(".wav") -> DetectedFormat.WAV
            clean.endsWith(".dts") -> DetectedFormat.DTS
            clean.endsWith(".webm") -> DetectedFormat.WEBM
            clean.endsWith(".mkv") || clean.endsWith(".mka") -> DetectedFormat.MKV
            clean.endsWith(".ts") -> DetectedFormat.MPEG_TS
            clean.endsWith(".flv") -> DetectedFormat.FLV
            clean.endsWith(".avi") -> DetectedFormat.AVI
            clean.endsWith(".mp4") || clean.endsWith(".m4v") || clean.endsWith(".mov") -> DetectedFormat.MP4
            else -> DetectedFormat.UNKNOWN
        }
    }

    /**
     * Sahte PNG / JPEG / GIF / BMP / metin başlığı ile gizlenmiş MPEG-TS segmentlerinde
     * gerçek `0x47` TS paket başlangıcını bulur (en az 3 ardışık 188-bayt senkron baytı doğrular).
     * Bulunamazsa `-1` döner.
     */
    fun findMpegTsSyncOffset(bytes: ByteArray, length: Int = bytes.size): Int {
        val limit = length.coerceAtMost(bytes.size)
        if (limit < 188) return -1
        // Tek paketlik çok küçük segment durumu (188..375 bayt)
        if (limit < 188 * 2 + 1) {
            return if (bytes[0] == 0x47.toByte()) 0 else -1
        }
        val maxSearch = (limit - 188 * 2).coerceAtMost(8192)
        for (offset in 0 until maxSearch) {
            if (bytes[offset] == 0x47.toByte() &&
                bytes[offset + 188] == 0x47.toByte() &&
                (offset + 376 >= limit || bytes[offset + 376] == 0x47.toByte())
            ) {
                return offset
            }
        }
        return -1
    }

    /**
     * Eğer segment sahte bir resim/metin başlığıyla gizlenmiş bir MPEG-TS segmentiyse
     * sahte ön eki keser; değilse diziyi olduğu gibi döndürür.
     */
    fun sanitizeDisguisedTsSegment(bytes: ByteArray): ByteArray {
        if (bytes.isEmpty()) return bytes
        if (bytes[0] == 0x47.toByte()) return bytes
        // Geçerli bir fMP4 / MP4 / WebM / Ogg / FLAC / WAV / ID3 / ADTS / AC3 segmentine dokunma
        if (bytes.size >= 8) {
            val box = String(bytes, 4, 4, Charsets.ISO_8859_1)
            if (box == "ftyp" || box == "styp" || box == "moof" || box == "sidx" || box == "moov") {
                return bytes
            }
        }
        if (bytes.size >= 4) {
            if (bytes[0] == 'I'.code.toByte() && bytes[1] == 'D'.code.toByte() && bytes[2] == '3'.code.toByte()) return bytes
            if (bytes[0] == 'O'.code.toByte() && bytes[1] == 'g'.code.toByte() && bytes[2] == 'g'.code.toByte() && bytes[3] == 'S'.code.toByte()) return bytes
            if (bytes[0] == 'f'.code.toByte() && bytes[1] == 'L'.code.toByte() && bytes[2] == 'a'.code.toByte() && bytes[3] == 'C'.code.toByte()) return bytes
            if (bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte()) return bytes
            if (bytes[0] == 'F'.code.toByte() && bytes[1] == 'L'.code.toByte() && bytes[2] == 'V'.code.toByte() && bytes[3] == 0x01.toByte()) return bytes
            if (bytes[0] == 0x1A.toByte() && bytes[1] == 0x45.toByte() && bytes[2] == 0xDF.toByte() && bytes[3] == 0xA3.toByte()) return bytes
        }
        val offset = findMpegTsSyncOffset(bytes)
        return if (offset > 0) bytes.copyOfRange(offset, bytes.size) else bytes
    }

    /**
     * RFC 8216 Packed Audio segmentlerinin başındaki bir veya birden fazla `ID3v2` etiketinin
     * bittiği bayt ofsetini döndürür.
     */
    fun skipLeadingId3Tags(bytes: ByteArray, length: Int = bytes.size): Int {
        val limit = length.coerceAtMost(bytes.size)
        var pos = 0
        while (pos + 10 <= limit &&
            bytes[pos] == 'I'.code.toByte() &&
            bytes[pos + 1] == 'D'.code.toByte() &&
            bytes[pos + 2] == '3'.code.toByte()
        ) {
            val flags = bytes[pos + 5].toInt() and 0xFF
            val footerSize = if ((flags and 0x10) != 0) 10 else 0
            val tagSize = ((bytes[pos + 6].toInt() and 0x7F) shl 21) or
                ((bytes[pos + 7].toInt() and 0x7F) shl 14) or
                ((bytes[pos + 8].toInt() and 0x7F) shl 7) or
                (bytes[pos + 9].toInt() and 0x7F)
            val next = pos + 10 + tagSize + footerSize
            if (next <= pos || next > limit) break
            pos = next
        }
        return pos
    }

    fun stripLeadingId3Tags(bytes: ByteArray): ByteArray {
        val offset = skipLeadingId3Tags(bytes)
        return if (offset in 1 until bytes.size) bytes.copyOfRange(offset, bytes.size) else bytes
    }

    fun preferredVideoExtension(format: DetectedFormat): String = when (format) {
        DetectedFormat.MKV -> "mkv"
        DetectedFormat.WEBM -> "webm"
        DetectedFormat.MPEG_TS -> "ts"
        else -> "mp4"
    }

    fun preferredAudioExtension(format: DetectedFormat): String = when (format) {
        DetectedFormat.M4A, DetectedFormat.MP4, DetectedFormat.FMP4_SEGMENT -> "m4a"
        DetectedFormat.ADTS_AAC -> "aac"
        DetectedFormat.MP3 -> "mp3"
        DetectedFormat.AC3 -> "ac3"
        DetectedFormat.EAC3 -> "eac3"
        DetectedFormat.OPUS_OGG -> "opus"
        DetectedFormat.VORBIS_OGG -> "ogg"
        DetectedFormat.FLAC -> "flac"
        DetectedFormat.WAV -> "wav"
        DetectedFormat.DTS -> "dts"
        DetectedFormat.WEBM -> "webm"
        DetectedFormat.MKV -> "mka"
        else -> "m4a"
    }

    fun audioExtensionForMime(mimeType: String?): String = when (mimeType?.lowercase(Locale.ROOT)) {
        "audio/mp4a-latm", "audio/mp4", "audio/aac" -> "m4a"
        "audio/mpeg", "audio/mpeg-l1", "audio/mpeg-l2", "audio/mp3" -> "mp3"
        "audio/eac3", "audio/eac3-joc", "audio/ec-3" -> "eac3"
        "audio/ac3" -> "ac3"
        "audio/opus" -> "opus"
        "audio/vorbis", "audio/ogg" -> "ogg"
        "audio/flac" -> "flac"
        "audio/raw", "audio/wav" -> "wav"
        "audio/vnd.dts", "audio/vnd.dts.hd" -> "dts"
        "audio/alac" -> "m4a"
        else -> "m4a"
    }

    private fun decodeAsciiHead(bytes: ByteArray, count: Int): String =
        String(bytes, 0, count.coerceAtMost(bytes.size), Charsets.ISO_8859_1)
}
