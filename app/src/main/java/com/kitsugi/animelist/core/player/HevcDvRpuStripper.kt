package com.kitsugi.animelist.core.player

import androidx.media3.common.util.UnstableApi
import java.io.ByteArrayOutputStream

internal class ExposedByteArrayOutputStream(size: Int) : ByteArrayOutputStream(size) {
    fun backingArray(): ByteArray = buf
}

/**
 * Strips Dolby Vision RPU NAL units (HEVC NAL type 62) from an HEVC bitstream
 * on-the-fly, leaving the HDR10/HDR10+ base layer intact.
 */
@UnstableApi
internal object HevcDvRpuStripper {

    private const val NAL_TYPE_DV_RPU = 62
    private const val NAL_TYPE_DV_EL = 63

    private const val NAL_TYPE_PREFIX_SEI = 39
    private const val SEI_PAYLOAD_TYPE_MASTERING_DISPLAY = 137
    private const val SEI_PAYLOAD_TYPE_CONTENT_LIGHT_LEVEL = 144

    // ── HDR10 static metadata probe (NuvioTV paritesi) ────────────────────────
    // Dolby Vision profil 5'in kendi başına görüntülenebilir bir HDR10 katmanı
    // YOKTUR: pikseller IPTPQc2 renk uzayındadır. RPU'yu sıyırmak görüntüyü
    // yeşil/mor yapar. Ancak hibrit remux'larda P5 taban katmanı, kendi HDR10
    // statik metadatasını (Mastering Display / Content Light Level SEI) taşır;
    // YALNIZCA o durumda sıyırma güvenlidir. Bu yardımcı, sıyırma kararının
    // gerçek ayırt edicisidir: her P5 karesinde RPU vardır, bu bir sinyal değil.

    /** Length-delimited (MP4/fMP4) örnekte HDR10 statik metadata SEI'si var mı? */
    fun containsHdr10StaticMetadataSei(
        sample: ByteArray,
        sampleLength: Int,
        nalUnitLengthFieldLength: Int
    ): Boolean {
        if (nalUnitLengthFieldLength !in 1..4) return false
        var offset = 0
        while (offset + nalUnitLengthFieldLength <= sampleLength) {
            var nalSize = 0
            for (i in 0 until nalUnitLengthFieldLength) {
                nalSize = (nalSize shl 8) or (sample[offset + i].toInt() and 0xFF)
            }
            offset += nalUnitLengthFieldLength
            if (nalSize <= 0 || offset + nalSize > sampleLength) break
            val nalType = (sample[offset].toInt() ushr 1) and 0x3F
            if (nalType == NAL_TYPE_PREFIX_SEI && hasStaticMetadataPayload(sample, offset, nalSize)) {
                return true
            }
            offset += nalSize
        }
        return false
    }

    /** Annex-B (TS/raw HEVC) örnekte HDR10 statik metadata SEI'si var mı? */
    fun containsHdr10StaticMetadataSeiAnnexB(sample: ByteArray, sampleLength: Int): Boolean {
        var scan = 0
        while (scan < sampleLength) {
            val startCode = findStartCode(sample, scan, sampleLength)
            if (startCode < 0) break
            val scLen = startCodeLength(sample, startCode, sampleLength)
            val nalBegin = startCode + scLen
            val nextStartCode = findStartCode(sample, nalBegin + 2, sampleLength)
            val nalEnd = if (nextStartCode < 0) sampleLength else nextStartCode
            if (nalBegin < nalEnd) {
                val nalType = (sample[nalBegin].toInt() ushr 1) and 0x3F
                if (nalType == NAL_TYPE_PREFIX_SEI &&
                    hasStaticMetadataPayload(sample, nalBegin, nalEnd - nalBegin)
                ) {
                    return true
                }
            }
            scan = nalEnd
        }
        return false
    }

    /**
     * Prefix SEI NAL içeriğinde (2 baytlık NAL başlığından sonra) Mastering
     * Display Colour Volume (137) veya Content Light Level Info (144) var mı?
     *
     * SEI mesajları `payloadType` (+0xFF uzatması) ve `payloadSize` ile
     * zincirlenir; tek bir karede birden fazla mesaj olabilir, bu yüzden
     * zinciri sonuna kadar yürüyoruz.
     */
    private fun hasStaticMetadataPayload(data: ByteArray, nalStart: Int, nalSize: Int): Boolean {
        var pos = nalStart + 2 // HEVC NAL başlığı 2 bayt
        val end = nalStart + nalSize
        while (pos < end) {
            var payloadType = 0
            while (pos < end && (data[pos].toInt() and 0xFF) == 0xFF) {
                payloadType += 255
                pos++
            }
            if (pos >= end) return false
            payloadType += data[pos].toInt() and 0xFF
            pos++

            var payloadSize = 0
            while (pos < end && (data[pos].toInt() and 0xFF) == 0xFF) {
                payloadSize += 255
                pos++
            }
            if (pos >= end) return false
            payloadSize += data[pos].toInt() and 0xFF
            pos++

            if (payloadType == SEI_PAYLOAD_TYPE_MASTERING_DISPLAY ||
                payloadType == SEI_PAYLOAD_TYPE_CONTENT_LIGHT_LEVEL
            ) {
                return true
            }
            pos += payloadSize
            // rbsp_trailing_bits: sonraki mesaj yok
            if (pos < end && (data[pos].toInt() and 0xFF) == 0x80) return false
        }
        return false
    }

    /**
     * Rewrites a length-delimited (MP4/fMP4) sample directly into a reusable
     * ExposedByteArrayOutputStream, removing any NAL unit whose type is 62 (DV RPU).
     * Returns true if anything was stripped, false otherwise.
     */
    fun stripRpuLengthDelimited(
        sample: ByteArray,
        sampleLen: Int,
        nalLengthFieldLength: Int,
        out: ExposedByteArrayOutputStream
    ): Boolean {
        if (sampleLen < nalLengthFieldLength) return false
        out.reset()
        var pos = 0
        var changed = false
        while (pos + nalLengthFieldLength <= sampleLen) {
            var nalSize = 0
            for (i in 0 until nalLengthFieldLength) {
                nalSize = (nalSize shl 8) or (sample[pos + i].toInt() and 0xFF)
            }
            val nalStart = pos + nalLengthFieldLength
            if (nalSize <= 0 || nalStart + nalSize > sampleLen) return false
            val nalHeader = sample[nalStart].toInt()
            val nalType = (nalHeader ushr 1) and 0x3F
            val layerId =
                if (nalStart + 1 < sampleLen) {
                    ((nalHeader and 0x01) shl 5) or
                            ((sample[nalStart + 1].toInt() and 0xF8) ushr 3)
                } else {
                    0
                }
            val isRpu = nalType == NAL_TYPE_DV_RPU
            val isEnhancementLayer = layerId > 0
            val shouldDrop = isRpu || isEnhancementLayer
            if (shouldDrop) {
                changed = true
            } else {
                for (i in nalLengthFieldLength - 1 downTo 0) {
                    out.write((nalSize ushr (i * 8)) and 0xFF)
                }
                out.write(sample, nalStart, nalSize)
            }
            pos = nalStart + nalSize
        }
        return changed
    }

    /**
     * Rewrites a length-delimited (MP4/fMP4) sample, removing any NAL unit
     * whose type is 62 (DV RPU). Returns the rewritten bytes, or null if
     * nothing was stripped (caller should use the original).
     */
    fun stripRpuLengthDelimited(
        sample: ByteArray,
        sampleLen: Int,
        nalLengthFieldLength: Int,
    ): ByteArray? {
        val out = ExposedByteArrayOutputStream(sampleLen)
        val changed = stripRpuLengthDelimited(sample, sampleLen, nalLengthFieldLength, out)
        return if (changed) out.toByteArray() else null
    }

    /**
     * Rewrites an Annex-B (TS/raw HEVC) sample directly into a reusable
     * ExposedByteArrayOutputStream, removing DV RPU NAL units.
     * Returns true if anything was stripped, false otherwise.
     */
    fun stripRpuAnnexB(
        sample: ByteArray,
        sampleLen: Int,
        out: ExposedByteArrayOutputStream
    ): Boolean {
        out.reset()
        var scan = 0
        var changed = false
        while (scan < sampleLen) {
            val startCode = findStartCode(sample, scan, sampleLen)
            if (startCode < 0) {
                out.write(sample, scan, sampleLen - scan)
                break
            }
            val scLen = startCodeLength(sample, startCode, sampleLen)
            val nalBegin = startCode + scLen
            val nextStartCode = findStartCode(sample, nalBegin + 2, sampleLen)
            val nalEnd = if (nextStartCode < 0) sampleLen else nextStartCode

            // Write any bytes before this start code
            if (startCode > scan) out.write(sample, scan, startCode - scan)

            if (nalBegin < nalEnd) {
                val nalType = (sample[nalBegin].toInt() ushr 1) and 0x3F
                val layerId =
                    if (nalBegin + 1 < nalEnd) {
                        ((sample[nalBegin].toInt() and 0x01) shl 5) or
                                ((sample[nalBegin + 1].toInt() ushr 3) and 0x1F)
                    } else {
                        0
                    }

                if (
                    nalType == NAL_TYPE_DV_RPU ||
                    nalType == NAL_TYPE_DV_EL ||
                    layerId > 0
                ) {
                    changed = true
                    // Drop start code + NAL payload entirely
                } else {
                    out.write(sample, startCode, nalEnd - startCode)
                }
            }
            scan = nalEnd
        }
        return changed
    }

    /**
     * Rewrites an Annex-B (TS/raw HEVC) sample, removing DV RPU NAL units.
     * Returns the rewritten bytes, or null if nothing was stripped.
     */
    fun stripRpuAnnexB(sample: ByteArray, sampleLen: Int): ByteArray? {
        val out = ExposedByteArrayOutputStream(sampleLen)
        val changed = stripRpuAnnexB(sample, sampleLen, out)
        return if (changed) out.toByteArray() else null
    }

    private fun findStartCode(data: ByteArray, from: Int, limit: Int): Int {
        var i = from
        while (i + 2 < limit) {
            if (data[i].toInt() == 0 && data[i + 1].toInt() == 0) {
                if (data[i + 2].toInt() == 1) return i
                if (i + 3 < limit && data[i + 2].toInt() == 0 && data[i + 3].toInt() == 1) return i
            }
            i++
        }
        return -1
    }

    private fun startCodeLength(data: ByteArray, offset: Int, limit: Int): Int {
        return if (offset + 3 < limit &&
            data[offset].toInt() == 0 &&
            data[offset + 1].toInt() == 0 &&
            data[offset + 2].toInt() == 0 &&
            data[offset + 3].toInt() == 1
        ) 4 else 3
    }
}
