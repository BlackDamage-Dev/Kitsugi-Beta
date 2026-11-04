package com.kitsugi.animelist.core.player.audio

import java.io.File
import java.io.RandomAccessFile

/**
 * FLV is used by a few plugin fallback hosts. Keep only its audio tags: AAC-LC gets
 * proper ADTS frames, MP3 gets its original frames. Video/script tags are never saved.
 * For other FLV codecs the worker may try the device's MediaExtractor instead.
 */
internal object FlvAudioExport {
    private const val MAX_TAG_BYTES = 32 * 1024 * 1024
    private const val MAX_ADTS_FRAME = 0x1fff

    private fun RandomAccessFile.read24(): Int =
        (readUnsignedByte() shl 16) or (readUnsignedByte() shl 8) or readUnsignedByte()

    /** MPEG-4 AudioSpecificConfig (AAC Main/LC/SSR). No silent SBR/HE-AAC mislabeling. */
    private fun adtsConfig(config: ByteArray): Pair<Int, Int> {
        require(config.size >= 2) { "FLV AAC yapılandırması eksik" }
        val first = config[0].toInt() and 255
        val second = config[1].toInt() and 255
        val objectType = first shr 3
        val rateIndex = ((first and 7) shl 1) or (second shr 7)
        val channels = (second shr 3) and 15
        require(objectType in 1..3 && rateIndex in 0..12 && channels in 1..7) {
            "FLV AAC profili veya kanalları ADTS için uygun değil"
        }
        return (((objectType - 1) shl 6) or (rateIndex shl 2) or (channels shr 2)) to (channels and 3)
    }

    fun adtsHeader(config: ByteArray, sampleBytes: Int): ByteArray {
        val (third, channels) = adtsConfig(config)
        val length = sampleBytes + 7
        require(sampleBytes > 0 && length <= MAX_ADTS_FRAME) { "FLV AAC karesi çok büyük/boş" }
        return byteArrayOf(
            0xff.toByte(), 0xf1.toByte(), third.toByte(),
            ((channels shl 6) or ((length shr 11) and 3)).toByte(),
            ((length shr 3) and 255).toByte(),
            (((length and 7) shl 5) or 0x1f).toByte(),
            0xfc.toByte()
        )
    }

    fun export(source: File, output: File, checkActive: () -> Unit = {}): AudioDemuxer.Result {
        RandomAccessFile(source, "r").use { file ->
            val length = file.length()
            require(length >= 13 && file.readUnsignedByte() == 'F'.code &&
                file.readUnsignedByte() == 'L'.code && file.readUnsignedByte() == 'V'.code &&
                file.readUnsignedByte() == 1) { "FLV dosyası geçersiz" }
            val flags = file.readUnsignedByte()
            require(flags and 4 != 0) { "FLV dosyasında ses yok" }
            val dataOffset = file.readInt().toLong() and 0xffff_ffffL
            require(dataOffset in 9..length - 4) { "FLV başlangıcı geçersiz" }
            file.seek(dataOffset)
            require(file.readInt() == 0) { "FLV başlangıç etiketi geçersiz" }
            var audioFormat = -1
            var config: ByteArray? = null
            var sampleCount = 0
            var bytesWritten = 0L
            var lastTime = -1L
            output.outputStream().buffered().use { out ->
                val buffer = ByteArray(64 * 1024)
                while (file.filePointer < length) {
                    checkActive()
                    require(length - file.filePointer >= 15) { "FLV etiketi kesilmiş" }
                    val tag = file.readUnsignedByte()
                    require(tag in setOf(8, 9, 18)) { "FLV şifreli/bilinmeyen etiket desteklenmiyor" }
                    val size = file.read24()
                    val timestamp = file.read24().toLong() or (file.readUnsignedByte().toLong() shl 24)
                    require(file.read24() == 0 && size in 0..MAX_TAG_BYTES &&
                        size.toLong() <= length - file.filePointer - 4) { "FLV etiket boyutu geçersiz" }
                    val end = file.filePointer + size
                    if (tag == 8 && size > 0) {
                        val header = file.readUnsignedByte()
                        val encoding = header shr 4
                        require(audioFormat == -1 || audioFormat == encoding) { "FLV ses kodeği değişti" }
                        audioFormat = encoding
                        when (encoding) {
                            10 -> {
                                require(size >= 2) { "FLV AAC etiketi eksik" }
                                when (file.readUnsignedByte()) {
                                    0 -> {
                                        require(sampleCount == 0 && size in 4..128) { "FLV AAC yapılandırması değişti" }
                                        config = ByteArray(size - 2).also(file::readFully)
                                        adtsConfig(config!!)
                                    }
                                    1 -> {
                                        val asc = config ?: error("FLV AAC yapılandırması bulunamadı")
                                        val sampleSize = size - 2
                                        val frame = adtsHeader(asc, sampleSize)
                                        require(timestamp >= lastTime) { "FLV ses zaman damgası kesintili" }
                                        require(bytesWritten + frame.size + sampleSize <= AudioExportHttp.MAX_TEMP_BYTES) {
                                            "Ses dosyası çok büyük"
                                        }
                                        out.write(frame)
                                        val packet = ByteArray(sampleSize)
                                        file.readFully(packet)
                                        out.write(packet)
                                        bytesWritten += frame.size + sampleSize
                                        sampleCount++
                                        lastTime = timestamp
                                    }
                                    2 -> Unit // End of AAC sequence.
                                    else -> error("FLV AAC paket türü desteklenmiyor")
                                }
                            }
                            2 -> {
                                val dataLength = size - 1
                                require(dataLength >= 4) { "FLV MP3 karesi eksik" }
                                val first = file.readUnsignedByte()
                                val second = file.readUnsignedByte()
                                require(first == 255 && second and 0xe0 == 0xe0 &&
                                    second and 0x18 != 0x08 && second and 0x06 != 0) { "FLV MP3 kare başlığı geçersiz" }
                                require(timestamp >= lastTime) { "FLV ses zaman damgası kesintili" }
                                require(bytesWritten + dataLength <= AudioExportHttp.MAX_TEMP_BYTES) { "Ses dosyası çok büyük" }
                                out.write(first); out.write(second)
                                var left = dataLength - 2
                                while (left > 0) {
                                    checkActive()
                                    val n = minOf(left, buffer.size)
                                    file.readFully(buffer, 0, n)
                                    out.write(buffer, 0, n)
                                    left -= n
                                }
                                bytesWritten += dataLength
                                sampleCount++
                                lastTime = timestamp
                            }
                            else -> error("Bu FLV ses kodeği cihazda çıkarılmalıdır: $encoding")
                        }
                    }
                    file.seek(end)
                    require((file.readInt().toLong() and 0xffff_ffffL) == size + 11L) {
                        "FLV etiket sonu geçersiz"
                    }
                }
            }
            require(sampleCount > 0 && bytesWritten == output.length()) { "FLV ses karesi bulunamadı" }
            return when (audioFormat) {
                10 -> AudioDemuxer.Result("aac", "audio/aac")
                2 -> AudioDemuxer.Result("mp3", "audio/mpeg")
                else -> error("FLV ses biçimi desteklenmiyor")
            }
        }
    }
}
