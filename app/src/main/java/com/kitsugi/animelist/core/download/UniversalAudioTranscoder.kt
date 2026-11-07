package com.kitsugi.animelist.core.download

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.ffmpeg.KitsugiFfmpegAudioBridge
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Cihazların donanımsal olarak desteklemediği veya konteyner uyumsuzluğu yaratan tüm ses kodeklerini
 * (E-AC-3 / Dolby Digital Plus / Atmos, AC-3, DTS / DTS-HD, TrueHD / MLP, Opus, Vorbis, FLAC, ALAC,
 * PCM / WAV / LPCM, MP1 / MP2, WMA, AMR, IAMF, MPEG-H ve 5.1 / 7.1 çok kanallı ses akışları)
 * otomatik olarak her cihazda sorunsuz çalışan evrensel **AAC-LC (.m4a)** formatına dönüştürür.
 *
 * Çözücü katmanı:
 *  1. Birincil: Uygulama içi paketli `libffmpegJNI.so` (`KitsugiFfmpegAudioBridge`) — OEM lisansına bağımlı kalmadan çözer.
 *  2. Yedek: Platform `MediaCodec` ses çözücüsü + ITU-R BS.775 stereo downmix & PCM dönüştürücü.
 *
 * Kodlayıcı katmanı:
 *  - Android standart `MediaCodec` `audio/mp4a-latm` (`AACObjectLC`, Stereo 192 kbps / Mono 128 kbps) + `MediaMuxer` (`MPEG_4` `.m4a`).
 */
@OptIn(UnstableApi::class)
object UniversalAudioTranscoder {

    private val SUPPORTED_AAC_SAMPLE_RATES = intArrayOf(
        8000, 11025, 12000, 16000, 22050, 24000, 32000, 44100, 48000
    )

    /**
     * Bağımsız ses indirmelerinde (`Download/Kitsugi/Audio`) yeniden kodlama gerektirmeden
     * doğrudan her cihazda ve müzik çalarda açılabilen evrensel format olup olmadığını döner.
     *
     * Yalnızca 1–2 kanallı (mono/stereo) standart **AAC-LC / HE-AAC** (`.m4a`) ve **MP3** (`.mp3`)
     * kayıpsız doğrudan aktarılır; diğer tüm kodekler ve 5.1/7.1 surround akışlar otomatik olarak
     * evrensel stereo AAC-LC `.m4a` formatına dönüştürülür.
     */
    fun isUniversalStandaloneCodec(
        sampleMimeType: String?,
        codecs: String? = null,
        channelCount: Int = 2
    ): Boolean {
        val mime = sampleMimeType?.lowercase()?.trim().orEmpty()
        if (mime.isEmpty()) return false
        if (channelCount > 2) return false
        val codecLower = codecs?.lowercase()?.trim().orEmpty()
        return when (mime) {
            MimeTypes.AUDIO_AAC, "audio/aac", "audio/mp4a" -> {
                // xHE-AAC / USAC (mp4a.40.42) eski cihazlarda açılmaz -> dönüştür
                !codecLower.contains("mp4a.40.42") && !codecLower.contains("usac")
            }
            MimeTypes.AUDIO_MPEG, "audio/mp3" -> true
            else -> false
        }
    }

    /**
     * `.mp4` video konteyneri içerisine tüm platformlarda (Android Galeri, iOS, Windows, TV)
     * sessiz kalmadan ve hata vermeden doğrudan gömülebilen ses formatı olup olmadığını döner.
     */
    fun isUniversalMp4VideoMuxCodec(
        sampleMimeType: String?,
        codecs: String? = null,
        channelCount: Int = 2
    ): Boolean {
        val mime = sampleMimeType?.lowercase()?.trim().orEmpty()
        if (channelCount > 2) return false
        val codecLower = codecs?.lowercase()?.trim().orEmpty()
        return (mime == MimeTypes.AUDIO_AAC || mime == "audio/aac") &&
            !codecLower.contains("mp4a.40.42") &&
            !codecLower.contains("usac")
    }

    /**
     * Standart AAC kodlayıcının kabul ettiği hedef örnekleme hızını seçer.
     * 88.2 / 176.4 kHz -> 44.1 kHz; 96 / 192 kHz -> 48 kHz.
     */
    fun chooseTargetSampleRate(inputSampleRate: Int): Int {
        if (inputSampleRate <= 0) return 48_000
        if (SUPPORTED_AAC_SAMPLE_RATES.contains(inputSampleRate)) return inputSampleRate
        if (inputSampleRate % 44_100 == 0) return 44_100
        if (inputSampleRate % 48_000 == 0) return 48_000
        return SUPPORTED_AAC_SAMPLE_RATES.minByOrNull { kotlin.math.abs(it - inputSampleRate.coerceIn(8_000, 48_000)) }
            ?: 48_000
    }

    /**
     * Kaynak medya dosyasındaki hedef ses izini evrensel Stereo/Mono **AAC-LC `.m4a`** dosyasına dönüştürür.
     */
    fun transcodeTrackToUniversalM4a(
        sourceFile: File,
        targetTrack: MediaTrackRemuxer.ProbedMediaTrack,
        outM4aFile: File,
        onProgress: ((Float) -> Unit)? = null
    ): Boolean {
        outM4aFile.parentFile?.mkdirs()
        if (outM4aFile.exists()) outM4aFile.delete()

        val format = targetTrack.media3Format ?: buildFallbackFormat(targetTrack)
        val inChannels = format.channelCount.takeIf { it > 0 } ?: 2
        val desiredChannels = if (inChannels == 1) 1 else 2

        if (format.sampleMimeType == MimeTypes.AUDIO_RAW) {
            val rawOk = runCatching {
                transcodeRawPcmDirectly(sourceFile, targetTrack, format, desiredChannels, outM4aFile, onProgress)
            }.getOrDefault(false)
            if (rawOk && outM4aFile.exists() && outM4aFile.length() > 256L) {
                return true
            }
            outM4aFile.delete()
        }

        val ffmpegOk = runCatching {
            transcodeViaFfmpegBridge(sourceFile, targetTrack, format, desiredChannels, outM4aFile, onProgress)
        }.getOrDefault(false)
        if (ffmpegOk && outM4aFile.exists() && outM4aFile.length() > 256L) {
            return true
        }

        outM4aFile.delete()
        val platformOk = runCatching {
            transcodeViaPlatformMediaCodec(sourceFile, targetTrack, format, desiredChannels, outM4aFile, onProgress)
        }.getOrDefault(false)
        if (platformOk && outM4aFile.exists() && outM4aFile.length() > 256L) {
            return true
        }

        outM4aFile.delete()
        return false
    }

    private fun buildFallbackFormat(track: MediaTrackRemuxer.ProbedMediaTrack): Format {
        return Format.Builder()
            .setSampleMimeType(track.sampleMimeType)
            .setCodecs(track.codecs.takeIf { it.isNotBlank() })
            .setChannelCount(track.channelCount.takeIf { it > 0 } ?: 2)
            .setSampleRate(track.sampleRate.takeIf { it > 0 } ?: 48_000)
            .setAverageBitrate(track.bitrate.takeIf { it > 0 } ?: Format.NO_VALUE)
            .setInitializationData(track.initializationData)
            .build()
    }

    private fun transcodeRawPcmDirectly(
        sourceFile: File,
        targetTrack: MediaTrackRemuxer.ProbedMediaTrack,
        format: Format,
        desiredChannels: Int,
        outM4aFile: File,
        onProgress: ((Float) -> Unit)?
    ): Boolean {
        val inRate = format.sampleRate.takeIf { it > 0 } ?: 44_100
        val inCh = format.channelCount.takeIf { it > 0 } ?: 2
        val pcmEnc = format.pcmEncoding.takeIf { it != Format.NO_VALUE } ?: C.ENCODING_PCM_16BIT
        val targetRate = chooseTargetSampleRate(inRate)
        var sink: AacM4aEncoderSink? = null
        try {
            MediaTrackRemuxer.streamSingleTrackSamples(sourceFile, targetTrack.extractorTrackId) { liveFmt, sampleBytes, _, _ ->
                if (sampleBytes.isEmpty()) return@streamSingleTrackSamples
                val activeEnc = liveFmt?.pcmEncoding?.takeIf { it != Format.NO_VALUE } ?: pcmEnc
                val pcm16 = convertPcmTo16BitLe(sampleBytes, activeEnc)
                val prepared = normalizeChannelsAndRate(
                    pcm16Le = pcm16,
                    inChannels = inCh,
                    targetChannels = desiredChannels,
                    inRate = inRate,
                    targetRate = targetRate
                )
                if (prepared.isNotEmpty()) {
                    val activeSink = sink ?: AacM4aEncoderSink(
                        outFile = outM4aFile,
                        sampleRate = targetRate,
                        channelCount = desiredChannels
                    ).also { sink = it }
                    activeSink.feedPcm16Le(prepared)
                }
            }
            val completedSink = sink ?: return false
            completedSink.finishAndClose()
            onProgress?.invoke(1f)
            return outM4aFile.exists() && outM4aFile.length() > 256L
        } catch (t: Throwable) {
            runCatching { sink?.abort() }
            throw t
        }
    }

    private fun transcodeViaFfmpegBridge(
        sourceFile: File,
        targetTrack: MediaTrackRemuxer.ProbedMediaTrack,
        format: Format,
        desiredChannels: Int,
        outM4aFile: File,
        onProgress: ((Float) -> Unit)?
    ): Boolean {
        val session = KitsugiFfmpegAudioBridge.openSession(format, desiredChannels) ?: return false
        var sink: AacM4aEncoderSink? = null
        try {
            session.use { ff ->
                var sampleCount = 0L
                MediaTrackRemuxer.streamSingleTrackSamples(sourceFile, targetTrack.extractorTrackId) { _, sampleBytes, timeUs, sampleFlags ->
                    if (sampleBytes.isEmpty()) return@streamSingleTrackSamples
                    val flags = if ((sampleFlags and C.BUFFER_FLAG_KEY_FRAME) != 0) C.BUFFER_FLAG_KEY_FRAME else 0
                    val decodedList = ff.queueSample(sampleBytes, timeUs, flags)
                    for (pcmChunk in decodedList) {
                        if (pcmChunk.pcm16LeBytes.isEmpty()) continue
                        val actualInRate = ff.sampleRate.takeIf { it > 0 }
                            ?: format.sampleRate.takeIf { it > 0 }
                            ?: 48_000
                        val actualInCh = ff.channelCount.takeIf { it > 0 } ?: desiredChannels
                        val preparedPcm = normalizeChannelsAndRate(
                            pcm16Le = pcmChunk.pcm16LeBytes,
                            inChannels = actualInCh,
                            targetChannels = desiredChannels,
                            inRate = actualInRate,
                            targetRate = chooseTargetSampleRate(actualInRate)
                        )
                        val activeSink = sink ?: AacM4aEncoderSink(
                            outFile = outM4aFile,
                            sampleRate = chooseTargetSampleRate(actualInRate),
                            channelCount = desiredChannels
                        ).also { sink = it }
                        activeSink.feedPcm16Le(preparedPcm)
                    }
                    sampleCount++
                    if (sampleCount % 200L == 0L) {
                        onProgress?.invoke(0.5f)
                    }
                }

                val tail = ff.finishAndDrain()
                for (pcmChunk in tail) {
                    if (pcmChunk.pcm16LeBytes.isEmpty()) continue
                    val actualInRate = ff.sampleRate.takeIf { it > 0 }
                        ?: format.sampleRate.takeIf { it > 0 }
                        ?: 48_000
                    val actualInCh = ff.channelCount.takeIf { it > 0 } ?: desiredChannels
                    val targetRate = chooseTargetSampleRate(actualInRate)
                    val preparedPcm = normalizeChannelsAndRate(
                        pcm16Le = pcmChunk.pcm16LeBytes,
                        inChannels = actualInCh,
                        targetChannels = desiredChannels,
                        inRate = actualInRate,
                        targetRate = targetRate
                    )
                    val activeSink = sink ?: AacM4aEncoderSink(
                        outFile = outM4aFile,
                        sampleRate = targetRate,
                        channelCount = desiredChannels
                    ).also { sink = it }
                    activeSink.feedPcm16Le(preparedPcm)
                }
            }
            val completedSink = sink ?: return false
            completedSink.finishAndClose()
            onProgress?.invoke(1f)
            return outM4aFile.exists() && outM4aFile.length() > 256L
        } catch (t: Throwable) {
            runCatching { sink?.abort() }
            throw t
        }
    }

    private fun transcodeViaPlatformMediaCodec(
        sourceFile: File,
        targetTrack: MediaTrackRemuxer.ProbedMediaTrack,
        format: Format,
        desiredChannels: Int,
        outM4aFile: File,
        onProgress: ((Float) -> Unit)?
    ): Boolean {
        val mime = format.sampleMimeType ?: return false
        val inRate = format.sampleRate.takeIf { it > 0 } ?: 48_000
        val inCh = format.channelCount.takeIf { it > 0 } ?: 2

        val mediaFormat = MediaFormat.createAudioFormat(mime, inRate, inCh)
        if (format.maxInputSize > 0) {
            mediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, maxOf(format.maxInputSize, 65_536))
        }
        if (Build.VERSION.SDK_INT >= 31) {
            mediaFormat.setInteger(MediaFormat.KEY_MAX_OUTPUT_CHANNEL_COUNT, desiredChannels)
        }
        format.initializationData.forEachIndexed { idx, bytes ->
            mediaFormat.setByteBuffer("csd-$idx", ByteBuffer.wrap(bytes))
        }

        val decoder = MediaCodec.createDecoderByType(mime)
        var sink: AacM4aEncoderSink? = null
        try {
            decoder.configure(mediaFormat, null, null, 0)
            decoder.start()

            var outSampleRate = inRate
            var outChannels = inCh
            var outPcmEncoding = AudioFormat.ENCODING_PCM_16BIT
            val decInfo = MediaCodec.BufferInfo()

            fun drainDecoder(waitForEos: Boolean) {
                var eos = false
                var idleLoops = 0
                while (true) {
                    val outIdx = decoder.dequeueOutputBuffer(decInfo, if (waitForEos) 10_000L else 0L)
                    when {
                        outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            if (!waitForEos || eos || ++idleLoops > 300) break
                        }
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val of = decoder.outputFormat
                            if (of.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                                outSampleRate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            }
                            if (of.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                                outChannels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            }
                            if (Build.VERSION.SDK_INT >= 24 && of.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                                outPcmEncoding = of.getInteger(MediaFormat.KEY_PCM_ENCODING)
                            }
                        }
                        outIdx >= 0 -> {
                            idleLoops = 0
                            val outBuf = decoder.getOutputBuffer(outIdx)
                            if (outBuf != null && decInfo.size > 0) {
                                outBuf.position(decInfo.offset)
                                outBuf.limit(decInfo.offset + decInfo.size)
                                val rawBytes = ByteArray(decInfo.size)
                                outBuf.get(rawBytes)
                                val pcm16 = convertPcmTo16BitLe(rawBytes, outPcmEncoding)
                                val targetRate = chooseTargetSampleRate(outSampleRate)
                                val prepared = normalizeChannelsAndRate(
                                    pcm16Le = pcm16,
                                    inChannels = outChannels,
                                    targetChannels = desiredChannels,
                                    inRate = outSampleRate,
                                    targetRate = targetRate
                                )
                                if (prepared.isNotEmpty()) {
                                    val activeSink = sink ?: AacM4aEncoderSink(
                                        outFile = outM4aFile,
                                        sampleRate = targetRate,
                                        channelCount = desiredChannels
                                    ).also { sink = it }
                                    activeSink.feedPcm16Le(prepared)
                                }
                            }
                            if ((decInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                eos = true
                            }
                            decoder.releaseOutputBuffer(outIdx, false)
                            if (eos) break
                        }
                        else -> if (!waitForEos) break
                    }
                }
            }

            MediaTrackRemuxer.streamSingleTrackSamples(sourceFile, targetTrack.extractorTrackId) { _, sampleBytes, timeUs, sampleFlags ->
                if (sampleBytes.isEmpty()) return@streamSingleTrackSamples
                var offset = 0
                while (offset < sampleBytes.size) {
                    drainDecoder(waitForEos = false)
                    val inIdx = decoder.dequeueInputBuffer(10_000L)
                    if (inIdx < 0) continue
                    val inBuf = decoder.getInputBuffer(inIdx) ?: continue
                    inBuf.clear()
                    val toCopy = minOf(sampleBytes.size - offset, inBuf.remaining())
                    inBuf.put(sampleBytes, offset, toCopy)
                    val flags = if ((sampleFlags and C.BUFFER_FLAG_KEY_FRAME) != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                    decoder.queueInputBuffer(inIdx, 0, toCopy, timeUs.coerceAtLeast(0L), flags)
                    offset += toCopy
                }
                drainDecoder(waitForEos = false)
            }

            var queuedEos = false
            var eosSpins = 0
            while (!queuedEos && eosSpins < 300) {
                drainDecoder(waitForEos = false)
                val inIdx = decoder.dequeueInputBuffer(10_000L)
                if (inIdx >= 0) {
                    decoder.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    queuedEos = true
                } else {
                    eosSpins++
                }
            }
            drainDecoder(waitForEos = true)

            val completedSink = sink ?: return false
            completedSink.finishAndClose()
            onProgress?.invoke(1f)
            return outM4aFile.exists() && outM4aFile.length() > 256L
        } finally {
            runCatching { decoder.stop() }
            runCatching { decoder.release() }
            runCatching { sink?.abort() }
        }
    }

    /**
     * Ham PCM kodlamalarını (Float 32-bit, 24-bit packed, 32-bit int, 8-bit unsigned)
     * standart 16-bit signed little-endian PCM dizisine çevirir.
     */
    fun convertPcmTo16BitLe(rawBytes: ByteArray, pcmEncoding: Int): ByteArray {
        if (rawBytes.isEmpty()) return rawBytes
        return when (pcmEncoding) {
            AudioFormat.ENCODING_PCM_16BIT, C.ENCODING_PCM_16BIT -> rawBytes
            AudioFormat.ENCODING_PCM_FLOAT, C.ENCODING_PCM_FLOAT -> {
                val floatCount = rawBytes.size / 4
                val src = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN)
                val dst = ByteBuffer.allocate(floatCount * 2).order(ByteOrder.LITTLE_ENDIAN)
                for (i in 0 until floatCount) {
                    val f = src.float.coerceIn(-1.0f, 1.0f)
                    val s = (f * 32767.0f).roundToInt().coerceIn(-32768, 32767).toShort()
                    dst.putShort(s)
                }
                dst.array()
            }
            AudioFormat.ENCODING_PCM_8BIT, C.ENCODING_PCM_8BIT -> {
                val dst = ByteBuffer.allocate(rawBytes.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                for (b in rawBytes) {
                    val unsigned = b.toInt() and 0xFF
                    val s = ((unsigned - 128) shl 8).coerceIn(-32768, 32767).toShort()
                    dst.putShort(s)
                }
                dst.array()
            }
            21 /* ENCODING_PCM_24BIT_PACKED */, C.ENCODING_PCM_24BIT -> {
                val sampleCount = rawBytes.size / 3
                val dst = ByteBuffer.allocate(sampleCount * 2).order(ByteOrder.LITTLE_ENDIAN)
                var idx = 0
                for (i in 0 until sampleCount) {
                    // Little-endian 24-bit: üst 16 bit [idx+1, idx+2]
                    val lo = rawBytes[idx + 1].toInt() and 0xFF
                    val hi = rawBytes[idx + 2].toInt()
                    dst.putShort(((hi shl 8) or lo).toShort())
                    idx += 3
                }
                dst.array()
            }
            22 /* ENCODING_PCM_32BIT */, C.ENCODING_PCM_32BIT -> {
                val sampleCount = rawBytes.size / 4
                val src = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN)
                val dst = ByteBuffer.allocate(sampleCount * 2).order(ByteOrder.LITTLE_ENDIAN)
                for (i in 0 until sampleCount) {
                    val v = src.int
                    dst.putShort((v shr 16).toShort())
                }
                dst.array()
            }
            else -> rawBytes
        }
    }

    /**
     * Çok kanallı (3.0, 4.0, 5.0, 5.1, 6.1, 7.1) 16-bit LE PCM ses akışını
     * ITU-R BS.775 katsayılarıyla konuşma (Center) kanalını kaybetmeden Stereo (2 kanal) PCM'e indirger.
     */
    fun downmixToStereoPcm16Le(pcm16Le: ByteArray, inChannels: Int): ByteArray {
        if (inChannels <= 2 || pcm16Le.isEmpty()) return pcm16Le
        val frameBytes = inChannels * 2
        val frameCount = pcm16Le.size / frameBytes
        if (frameCount <= 0) return ByteArray(0)

        val src = ByteBuffer.wrap(pcm16Le, 0, frameCount * frameBytes).order(ByteOrder.LITTLE_ENDIAN)
        val dst = ByteBuffer.allocate(frameCount * 4).order(ByteOrder.LITTLE_ENDIAN)
        val invSqrt2 = 0.70710678

        for (f in 0 until frameCount) {
            val ch = ShortArray(inChannels) { src.short }
            var left: Double
            var right: Double
            when (inChannels) {
                3 -> {
                    // FL, FR, FC
                    val c = ch[2] * invSqrt2
                    left = ch[0] + c
                    right = ch[1] + c
                }
                4 -> {
                    // FL, FR, RL, RR
                    left = ch[0] + ch[2] * invSqrt2
                    right = ch[1] + ch[3] * invSqrt2
                }
                5 -> {
                    // FL, FR, FC, SL, SR
                    val c = ch[2] * invSqrt2
                    left = ch[0] + c + ch[3] * invSqrt2
                    right = ch[1] + c + ch[4] * invSqrt2
                }
                6 -> {
                    // 5.1: FL, FR, FC, LFE, SL, SR
                    val c = ch[2] * invSqrt2
                    val lfe = ch[3] * 0.25
                    left = ch[0] + c + lfe + ch[4] * invSqrt2
                    right = ch[1] + c + lfe + ch[5] * invSqrt2
                }
                7 -> {
                    // 6.1: FL, FR, FC, LFE, BC, SL, SR
                    val c = ch[2] * invSqrt2
                    val bc = ch[4] * 0.5
                    left = ch[0] + c + bc + ch[5] * invSqrt2
                    right = ch[1] + c + bc + ch[6] * invSqrt2
                }
                8 -> {
                    // 7.1: FL, FR, FC, LFE, RL, RR, SL, SR
                    val c = ch[2] * invSqrt2
                    val lfe = ch[3] * 0.25
                    left = ch[0] + c + lfe + (ch[4] + ch[6]) * 0.5
                    right = ch[1] + c + lfe + (ch[5] + ch[7]) * 0.5
                }
                else -> {
                    left = ch[0].toDouble()
                    right = ch[1].toDouble()
                }
            }
            // Clipping önleyici normalize kazanç (~ -2.5 dB çok kanallı tepe koruması)
            val normGain = 0.75
            val outL = (left * normGain).roundToInt().coerceIn(-32768, 32767).toShort()
            val outR = (right * normGain).roundToInt().coerceIn(-32768, 32767).toShort()
            dst.putShort(outL)
            dst.putShort(outR)
        }
        return dst.array()
    }

    /**
     * Doğrusal enterpolasyonlu 16-bit LE PCM örnekleme hızı dönüştürücü (örn. 96 kHz / 192 kHz -> 48 kHz).
     */
    fun resamplePcm16Le(
        pcm16Le: ByteArray,
        channels: Int,
        inSampleRate: Int,
        outSampleRate: Int
    ): ByteArray {
        if (inSampleRate <= 0 || outSampleRate <= 0 || inSampleRate == outSampleRate || pcm16Le.isEmpty()) {
            return pcm16Le
        }
        val ch = channels.coerceAtLeast(1)
        val frameBytes = ch * 2
        val inFrames = pcm16Le.size / frameBytes
        if (inFrames <= 1) return pcm16Le

        val outFrames = ((inFrames.toLong() * outSampleRate.toLong()) / inSampleRate.toLong()).toInt().coerceAtLeast(1)
        val src = ByteBuffer.wrap(pcm16Le, 0, inFrames * frameBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val dst = ByteBuffer.allocate(outFrames * frameBytes).order(ByteOrder.LITTLE_ENDIAN)
        val step = inSampleRate.toDouble() / outSampleRate.toDouble()

        for (outIdx in 0 until outFrames) {
            val pos = outIdx * step
            val i0 = pos.toInt().coerceIn(0, inFrames - 1)
            val i1 = (i0 + 1).coerceAtMost(inFrames - 1)
            val frac = pos - i0
            for (c in 0 until ch) {
                val s0 = src.get(i0 * ch + c).toInt()
                val s1 = src.get(i1 * ch + c).toInt()
                val interp = (s0 + (s1 - s0) * frac).roundToInt().coerceIn(-32768, 32767).toShort()
                dst.putShort(interp)
            }
        }
        return dst.array()
    }

    private fun normalizeChannelsAndRate(
        pcm16Le: ByteArray,
        inChannels: Int,
        targetChannels: Int,
        inRate: Int,
        targetRate: Int
    ): ByteArray {
        var current = pcm16Le
        var ch = inChannels.coerceAtLeast(1)
        if (ch > 2 && targetChannels == 2) {
            current = downmixToStereoPcm16Le(current, ch)
            ch = 2
        } else if (ch == 2 && targetChannels == 1) {
            val frames = current.size / 4
            val src = ByteBuffer.wrap(current, 0, frames * 4).order(ByteOrder.LITTLE_ENDIAN)
            val dst = ByteBuffer.allocate(frames * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until frames) {
                val m = ((src.short.toInt() + src.short.toInt()) / 2).toShort()
                dst.putShort(m)
            }
            current = dst.array()
            ch = 1
        }
        if (inRate != targetRate) {
            current = resamplePcm16Le(current, ch, inRate, targetRate)
        }
        return current
    }

    /**
     * 16-bit LE PCM ses verisini donanımsal/yazılımsal Android `MediaCodec` (`audio/mp4a-latm`, `AACObjectLC`)
     * ile kodlayıp standart ISO `.m4a` (`MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4`) konteynerine yazar.
     */
    private class AacM4aEncoderSink(
        private val outFile: File,
        private val sampleRate: Int,
        private val channelCount: Int
    ) {
        private val encoder: MediaCodec = MediaCodec.createEncoderByType(MimeTypes.AUDIO_AAC)
        private val muxer: MediaMuxer
        private val bufferInfo = MediaCodec.BufferInfo()
        private var muxerTrackIndex = -1
        private var muxerStarted = false
        private var totalSubmittedFrames = 0L
        private var lastWrittenPtsUs = -1L
        private var closed = false

        init {
            val bitrate = if (channelCount <= 1) 128_000 else 192_000
            val encFormat = MediaFormat.createAudioFormat(MimeTypes.AUDIO_AAC, sampleRate, channelCount).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 65_536)
            }
            encoder.configure(encFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
            muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        }

        fun feedPcm16Le(pcmBytes: ByteArray) {
            val frameBytes = (channelCount * 2).coerceAtLeast(2)
            val usableLen = (pcmBytes.size / frameBytes) * frameBytes
            var offset = 0
            while (offset < usableLen) {
                drainEncoder(waitForEos = false)
                val inIdx = encoder.dequeueInputBuffer(10_000L)
                if (inIdx < 0) continue
                val inBuf = encoder.getInputBuffer(inIdx) ?: continue
                inBuf.clear()
                val maxChunk = (inBuf.remaining() / frameBytes) * frameBytes
                if (maxChunk <= 0) continue
                val toWrite = minOf(usableLen - offset, maxChunk)
                inBuf.put(pcmBytes, offset, toWrite)
                val ptsUs = (totalSubmittedFrames * 1_000_000L) / sampleRate.toLong()
                encoder.queueInputBuffer(inIdx, 0, toWrite, ptsUs, 0)
                totalSubmittedFrames += (toWrite / frameBytes).toLong()
                offset += toWrite
            }
            drainEncoder(waitForEos = false)
        }

        fun finishAndClose() {
            if (closed) return
            var sentEos = false
            var spins = 0
            while (!sentEos && spins < 300) {
                drainEncoder(waitForEos = false)
                val inIdx = encoder.dequeueInputBuffer(10_000L)
                if (inIdx >= 0) {
                    val ptsUs = (totalSubmittedFrames * 1_000_000L) / sampleRate.toLong()
                    encoder.queueInputBuffer(inIdx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    sentEos = true
                } else {
                    spins++
                }
            }
            drainEncoder(waitForEos = true)
            closeInternal()
        }

        fun abort() {
            closeInternal()
        }

        private fun drainEncoder(waitForEos: Boolean) {
            var eos = false
            var idle = 0
            while (true) {
                val outIdx = encoder.dequeueOutputBuffer(bufferInfo, if (waitForEos) 10_000L else 0L)
                when {
                    outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (!waitForEos || eos || ++idle > 300) break
                    }
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (!muxerStarted) {
                            muxerTrackIndex = muxer.addTrack(encoder.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                    }
                    outIdx >= 0 -> {
                        idle = 0
                        val outBuf = encoder.getOutputBuffer(outIdx)
                        val isConfig = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                        if (!isConfig && outBuf != null && bufferInfo.size > 0 && muxerStarted) {
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            if (bufferInfo.presentationTimeUs <= lastWrittenPtsUs) {
                                bufferInfo.presentationTimeUs = lastWrittenPtsUs + 1L
                            }
                            lastWrittenPtsUs = bufferInfo.presentationTimeUs
                            muxer.writeSampleData(muxerTrackIndex, outBuf, bufferInfo)
                        }
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            eos = true
                        }
                        encoder.releaseOutputBuffer(outIdx, false)
                        if (eos) break
                    }
                    else -> if (!waitForEos) break
                }
            }
        }

        private fun closeInternal() {
            if (closed) return
            closed = true
            runCatching {
                if (muxerStarted) muxer.stop()
            }
            runCatching { muxer.release() }
            runCatching { encoder.stop() }
            runCatching { encoder.release() }
        }
    }
}
