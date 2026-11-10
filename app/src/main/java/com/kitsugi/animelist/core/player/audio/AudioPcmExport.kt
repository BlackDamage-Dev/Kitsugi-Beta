package com.kitsugi.animelist.core.player.audio

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Device decoder fallback. This is PCM16 WAV, not an MP3 encoder or a lossless codec copy. */
internal object AudioPcmExport {
    private const val MAX_WAV_BYTES = 0xffff_ffffL - 36

    fun toPcm16(bytes: ByteArray, encoding: Int): ByteArray {
        val width = when (encoding) {
            AudioFormat.ENCODING_PCM_8BIT -> 1
            AudioFormat.ENCODING_PCM_16BIT -> 2
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
            AudioFormat.ENCODING_PCM_32BIT, AudioFormat.ENCODING_PCM_FLOAT -> 4
            else -> error("Cihazın PCM biçimi desteklenmiyor: $encoding")
        }
        require(bytes.size % width == 0) { "Eksik PCM örneği" }
        val out = ByteArray(bytes.size / width * 2)
        for (i in 0 until bytes.size / width) {
            val pos = i * width
            val sample = when (encoding) {
                AudioFormat.ENCODING_PCM_8BIT -> ((bytes[pos].toInt() and 0xff) - 128) shl 8
                AudioFormat.ENCODING_PCM_16BIT ->
                    (bytes[pos].toInt() and 0xff) or (bytes[pos + 1].toInt() shl 8)
                AudioFormat.ENCODING_PCM_24BIT_PACKED ->
                    ((bytes[pos + 1].toInt() and 0xff) or (bytes[pos + 2].toInt() shl 8))
                AudioFormat.ENCODING_PCM_32BIT ->
                    ((bytes[pos + 2].toInt() and 0xff) or (bytes[pos + 3].toInt() shl 8))
                else -> {
                    val bits = (bytes[pos].toInt() and 0xff) or
                        ((bytes[pos + 1].toInt() and 0xff) shl 8) or
                        ((bytes[pos + 2].toInt() and 0xff) shl 16) or (bytes[pos + 3].toInt() shl 24)
                    val value = Float.fromBits(bits)
                    ((if (value.isNaN()) 0f else value.coerceIn(-1f, 1f)) * 32767f).toInt()
                }
            }
            out[i * 2] = sample.toByte()
            out[i * 2 + 1] = (sample shr 8).toByte()
        }
        return out
    }

    /** RIFF's size fields are little-endian unsigned 32-bit values (max ~4 GiB). */
    fun waveHeader(channels: Int, rate: Int, dataSize: Long): ByteArray {
        // RIFF PCM permits up to eight interleaved channels. Retain the decoder's order;
        // never downmix by dropping rear/center channels or publish a stereo-sized header.
        require(channels in 1..8 && rate in 8_000..192_000) { "WAV kanal sayısı veya örnekleme hızı desteklenmiyor" }
        require(dataSize in 1..MAX_WAV_BYTES && dataSize % (channels * 2) == 0L) { "WAV boyutu geçersiz" }
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII)).putInt((dataSize + 36).toInt())
        header.put("WAVEfmt ".toByteArray(Charsets.US_ASCII)).putInt(16)
        header.putShort(1.toShort()).putShort(channels.toShort()).putInt(rate)
        header.putInt(rate * channels * 2).putShort((channels * 2).toShort()).putShort(16.toShort())
        header.put("data".toByteArray(Charsets.US_ASCII)).putInt(dataSize.toInt())
        return header.array()
    }

    private fun RandomAccessFile.writeWaveHeader(channels: Int, rate: Int, dataSize: Long) {
        seek(0)
        write(waveHeader(channels, rate, dataSize))
    }

    private fun MediaFormat.number(key: String, fallback: Int = 0): Int =
        if (containsKey(key)) getInteger(key) else fallback

    suspend fun convert(extractor: MediaExtractor, format: MediaFormat, output: File) {
        val mime = format.getString(MediaFormat.KEY_MIME) ?: error("Ses kodeği bilinmiyor")
        var codec: MediaCodec? = null
        try {
            if (mime != "audio/raw") {
                codec = MediaCodec.createDecoderByType(mime)
                codec.configure(format, null, null, 0)
                codec.start()
            }
            RandomAccessFile(output, "rw").use { file ->
                file.setLength(44)
                file.seek(44)
                var bytesWritten = 0L
                var channels = 0
                var rate = 0
                fun writePcm(raw: ByteArray, decoded: MediaFormat) {
                    val currentChannels = decoded.number(MediaFormat.KEY_CHANNEL_COUNT)
                    val currentRate = decoded.number(MediaFormat.KEY_SAMPLE_RATE)
                    require(currentChannels in 1..8 && currentRate in 8_000..192_000) {
                        "Cihazın eksik veya sekizden fazla kanallı PCM çıkışı WAV'a çevrilemiyor"
                    }
                    if (channels == 0) { channels = currentChannels; rate = currentRate }
                    require(channels == currentChannels && rate == currentRate) { "Ses formatı kayıt sırasında değişti" }
                    val pcm = toPcm16(raw, decoded.number(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT))
                    require(pcm.size % (currentChannels * 2) == 0) { "Eksik çok kanallı PCM karesi" }
                    require(bytesWritten + pcm.size <= MAX_WAV_BYTES) { "WAV 4 GB sınırını aşıyor" }
                    file.write(pcm)
                    bytesWritten += pcm.size
                }
                if (codec == null) {
                    val buffer = ByteBuffer.allocate(8 * 1024 * 1024)
                    while (extractor.sampleTrackIndex >= 0) {
                        currentCoroutineContext().ensureActive()
                        require(extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED == 0) { "Şifreli ses çıkarılamıyor" }
                        require(extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME == 0) { "Eksik PCM örneği" }
                        buffer.clear()
                        val n = extractor.readSampleData(buffer, 0)
                        if (n < 0) break
                        require(n in 1 until buffer.capacity()) { "PCM örneği çok büyük" }
                        writePcm(buffer.array().copyOf(n), format)
                        extractor.advance()
                    }
                } else {
                    val info = MediaCodec.BufferInfo()
                    var inputDone = false
                    var outputDone = false
                    var idle = 0
                    while (!outputDone) {
                        currentCoroutineContext().ensureActive()
                        var moved = false
                        if (!inputDone) {
                            val index = codec.dequeueInputBuffer(10_000)
                            if (index >= 0) {
                                val buffer = codec.getInputBuffer(index) ?: error("Ses çözücü girişi açılamadı")
                                buffer.clear()
                                if (extractor.sampleTrackIndex >= 0) {
                                    require(extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED == 0) { "Şifreli ses çıkarılamıyor" }
                                    require(extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME == 0) { "Eksik ses örneği" }
                                }
                                val sample = extractor.readSampleData(buffer, 0)
                                if (sample < 0) {
                                    codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                    inputDone = true
                                } else {
                                    require(sample in 1..buffer.capacity()) { "Ses çözücü arabelleği yetersiz" }
                                    codec.queueInputBuffer(index, 0, sample, extractor.sampleTime, 0)
                                    extractor.advance()
                                }
                                moved = true
                            }
                        }
                        val index = codec.dequeueOutputBuffer(info, 10_000)
                        when {
                            index >= 0 -> {
                                if (info.size > 0) {
                                    val outputBuffer = codec.getOutputBuffer(index) ?: error("Ses çözücü çıktısı açılamadı")
                                    outputBuffer.position(info.offset)
                                    outputBuffer.limit(info.offset + info.size)
                                    writePcm(ByteArray(info.size).also { outputBuffer.get(it) }, codec.outputFormat)
                                }
                                outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                                codec.releaseOutputBuffer(index, false)
                                moved = true
                            }
                            index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                                // The first output buffer establishes sample rate/channel layout.
                                moved = true
                            }
                        }
                        idle = if (moved) 0 else idle + 1
                        require(idle < 3000) { "Ses çözücü yanıt vermiyor" }
                    }
                }
                require(bytesWritten > 0 && channels > 0) { "PCM ses üretilemedi" }
                file.writeWaveHeader(channels, rate, bytesWritten)
            }
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
        }
    }
}
