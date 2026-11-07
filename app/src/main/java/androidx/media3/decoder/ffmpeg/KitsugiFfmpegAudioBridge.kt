package androidx.media3.decoder.ffmpeg

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.decoder.SimpleDecoderOutputBuffer

/**
 * Kitsugi paket içi `lib-decoder-ffmpeg-release.aar` (`libffmpegJNI.so`) köprüsü.
 *
 * `FfmpegAudioDecoder` paket-içi görünürlüğe sahip olduğundan bu köprü `androidx.media3.decoder.ffmpeg`
 * paketinde yer alır ve E-AC-3 (Dolby Digital Plus / Atmos), AC-3, DTS, DTS-HD, TrueHD, MLP,
 * FLAC, ALAC, Opus, Vorbis, MP2, WMA, AMR, PCM/LPCM gibi cihazların donanımsal olarak
 * destekleyemediği tüm ses kodeklerini evrensel 16-bit PCM (stereo/mono) akışına çözer.
 */
@OptIn(UnstableApi::class)
object KitsugiFfmpegAudioBridge {

    class DecodedPcmChunk(
        val pcm16LeBytes: ByteArray,
        val timeUs: Long
    )

    class FfmpegPcmSession internal constructor(
        private val decoder: FfmpegAudioDecoder
    ) : AutoCloseable {

        val sampleRate: Int
            get() = decoder.sampleRate

        val channelCount: Int
            get() = decoder.channelCount

        val encoding: Int
            get() = decoder.encoding

        fun queueSample(data: ByteArray, timeUs: Long, flags: Int = 0): List<DecodedPcmChunk> {
            val out = ArrayList<DecodedPcmChunk>(4)
            var spins = 0
            while (true) {
                drainAvailable(out)
                val inBuf: DecoderInputBuffer? = decoder.dequeueInputBuffer()
                if (inBuf != null) {
                    inBuf.clear()
                    val padding = FfmpegLibrary.getInputBufferPaddingSize().coerceAtLeast(0)
                    inBuf.ensureSpaceForWrite(data.size + padding)
                    val buf = inBuf.data!!
                    buf.put(data)
                    if (padding > 0) {
                        buf.put(ByteArray(padding))
                        buf.limit(data.size)
                    }
                    inBuf.timeUs = timeUs.coerceAtLeast(0L)
                    inBuf.setFlags(flags)
                    inBuf.flip()
                    decoder.queueInputBuffer(inBuf)
                    drainAvailable(out)
                    return out
                }
                spins++
                if (spins > 500) {
                    throw IllegalStateException("FFmpeg ses çözücü giriş tamponu zaman aşımına uğradı")
                }
                Thread.sleep(2L)
            }
        }

        fun finishAndDrain(): List<DecodedPcmChunk> {
            val out = ArrayList<DecodedPcmChunk>(8)
            var sentEos = false
            var spins = 0
            while (!sentEos && spins < 500) {
                drainAvailable(out)
                val inBuf = decoder.dequeueInputBuffer()
                if (inBuf != null) {
                    inBuf.clear()
                    inBuf.timeUs = 0L
                    inBuf.addFlag(C.BUFFER_FLAG_END_OF_STREAM)
                    inBuf.flip()
                    decoder.queueInputBuffer(inBuf)
                    sentEos = true
                } else {
                    spins++
                    Thread.sleep(2L)
                }
            }

            var eosReached = false
            var drainSpins = 0
            while (!eosReached && drainSpins < 1000) {
                val outBuf: SimpleDecoderOutputBuffer? = decoder.dequeueOutputBuffer()
                if (outBuf == null) {
                    drainSpins++
                    Thread.sleep(2L)
                    continue
                }
                drainSpins = 0
                try {
                    if (outBuf.isEndOfStream) {
                        eosReached = true
                    }
                    val payload = outBuf.data
                    if (payload != null && payload.remaining() > 0) {
                        val pcm = ByteArray(payload.remaining())
                        payload.get(pcm)
                        out.add(DecodedPcmChunk(pcm, outBuf.timeUs))
                    }
                } finally {
                    outBuf.release()
                }
            }
            return out
        }

        private fun drainAvailable(into: MutableList<DecodedPcmChunk>) {
            while (true) {
                val outBuf: SimpleDecoderOutputBuffer = decoder.dequeueOutputBuffer() ?: break
                try {
                    val payload = outBuf.data
                    if (payload != null && payload.remaining() > 0) {
                        val pcm = ByteArray(payload.remaining())
                        payload.get(pcm)
                        into.add(DecodedPcmChunk(pcm, outBuf.timeUs))
                    }
                } finally {
                    outBuf.release()
                }
            }
        }

        override fun close() {
            runCatching { decoder.release() }
        }
    }

    fun isAvailableForMime(mimeType: String?): Boolean {
        if (mimeType.isNullOrBlank()) return false
        return runCatching {
            FfmpegLibrary.isAvailable() && FfmpegLibrary.supportsFormat(mimeType)
        }.getOrDefault(false)
    }

    fun openSession(format: Format, targetChannels: Int = 2): FfmpegPcmSession? {
        val mime = format.sampleMimeType ?: return null
        if (!isAvailableForMime(mime)) return null
        return runCatching {
            val inCh = format.channelCount.takeIf { it > 0 } ?: 2
            val outCh = if (inCh == 1 || targetChannels == 1) 1 else 2
            val requestDownmix = inCh > 2 || targetChannels in 1..2
            val layoutName = when {
                !requestDownmix -> null
                outCh == 1 -> "mono"
                else -> "stereo"
            }
            val initBufSize = maxOf(format.maxInputSize.takeIf { it > 0 } ?: 0, 65_536)
            val decoder = FfmpegAudioDecoder(
                format,
                /* numInputBuffers = */ 16,
                /* numOutputBuffers = */ 16,
                /* initialInputBufferSize = */ initBufSize,
                /* outputChannelCount = */ if (requestDownmix) outCh else 0,
                /* requestedOutputLayoutName = */ layoutName,
                /* outputEncoding = */ C.ENCODING_PCM_16BIT
            )
            decoder.setDownmixNormalizationEnabled(true)
            FfmpegPcmSession(decoder)
        }.getOrNull()
    }
}
