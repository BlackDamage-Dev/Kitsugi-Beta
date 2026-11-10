package com.kitsugi.animelist.core.download

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import kotlinx.coroutines.ensureActive
import java.io.File
import java.nio.ByteBuffer
import kotlin.coroutines.coroutineContext
import kotlin.math.abs

/**
 * Ayrık indirilen video + ses akışlarını (`#EXT-X-MEDIA:TYPE=AUDIO` veya `externalAudioUrl`)
 * ve tekil MPEG-TS akışlarını kayıpsız (re-encode yapmadan) standart `.mp4` / `.webm`
 * kapsayıcısına birleştiren yardımcı.
 *
 * Güvenlik garantisi:
 * - Kaynakta ses izi varken `MediaMuxer` ilgili ses codec'ini desteklemezse (ör. `E-AC-3`, `AC-3`,
 *   `DTS`, `MP3`) sessiz `.mp4` üretilmesi KESİNLİKLE engellenir:
 *   - Tekil MPEG-TS ise temizlenmiş `.ts` dosyası korunur (`Media3/FFmpeg` ve `MPV` tam sesli oynatır).
 *   - Ayrık video + ses ise yan ses dosyası (`<taban>.audio.<uzantı>`) korunur ve `OfflinePlaybackHelper`
 *     üzerinden `MergingMediaSource` / MPV `audio-file` ile tam sesli oynatılır.
 */
object MediaRemuxer {

    private const val TAG = "MediaRemuxer"
    private const val DEFAULT_BUFFER_SIZE = 2 * 1024 * 1024 // 2 MB (4K IDR kareleri için güvenli)
    private const val DISCONTINUITY_BACKWARD_THRESHOLD_US = 2_000_000L // 2 sn
    private const val DISCONTINUITY_FORWARD_THRESHOLD_US = 60_000_000L // 60 sn
    private const val DEFAULT_VIDEO_STEP_US = 33_333L
    private const val DEFAULT_AUDIO_STEP_US = 21_333L

    sealed class MuxOutcome {
        /** Video ve ses tek bir `.mp4` veya `.webm` dosyasında başarıyla birleştirildi. */
        data class MuxedSingleFile(val file: File, val extension: String) : MuxOutcome()

        /** Codec kombinasyonu tek kapsayıcıda birleştirilemedi; yan ses dosyası kullanılmalı. */
        data class CompanionAudioRequired(val reason: String) : MuxOutcome()
    }

    sealed class SingleTsOutcome {
        data class RemuxedToMp4(val mp4File: File) : SingleTsOutcome()
        data class KeepOriginalTs(val reason: String) : SingleTsOutcome()
    }

    /**
     * Ayrı video ve ses dosyalarını tek bir `.mp4` (veya VP8/VP9 + Opus/Vorbis ise `.webm`)
     * dosyasında zaman damgası sıralı (interleaved) olarak birleştirir.
     */
    suspend fun muxVideoAndAudio(
        videoFile: File,
        audioFile: File,
        outputDir: File,
        baseName: String,
        audioId3StartPtsUs: Long? = null
    ): MuxOutcome {
        if (!videoFile.isFile || videoFile.length() == 0L) {
            return MuxOutcome.CompanionAudioRequired("Video file missing or empty")
        }
        if (!audioFile.isFile || audioFile.length() == 0L) {
            return MuxOutcome.CompanionAudioRequired("Audio file missing or empty")
        }

        var videoExtractor: MediaExtractor? = null
        var audioExtractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var tempOutFile: File? = null

        return try {
            videoExtractor = MediaExtractor().apply { setDataSource(videoFile.absolutePath) }
            audioExtractor = MediaExtractor().apply { setDataSource(audioFile.absolutePath) }

            val videoTrackIdx = findFirstTrackIndex(videoExtractor, "video/")
                ?: return MuxOutcome.CompanionAudioRequired("MediaExtractor found no video track")
            val audioTrackIdx = findFirstTrackIndex(audioExtractor, "audio/")
                ?: return MuxOutcome.CompanionAudioRequired("MediaExtractor found no audio track")

            val videoFormat = videoExtractor.getTrackFormat(videoTrackIdx)
            val audioFormat = audioExtractor.getTrackFormat(audioTrackIdx)

            val videoMime = videoFormat.getString(MediaFormat.KEY_MIME).orEmpty()
            val audioMime = audioFormat.getString(MediaFormat.KEY_MIME).orEmpty()

            val useWebm = (videoMime.contains("vp8", ignoreCase = true) ||
                videoMime.contains("vp9", ignoreCase = true)) &&
                (audioMime.contains("opus", ignoreCase = true) ||
                    audioMime.contains("vorbis", ignoreCase = true))

            val outExt = if (useWebm) "webm" else "mp4"
            val muxerFormat = if (useWebm) {
                MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
            } else {
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            }

            outputDir.mkdirs()
            tempOutFile = File(outputDir, ".${baseName}_mux_tmp.$outExt").also {
                if (it.exists()) it.delete()
            }

            videoExtractor.selectTrack(videoTrackIdx)
            audioExtractor.selectTrack(audioTrackIdx)

            muxer = MediaMuxer(tempOutFile.absolutePath, muxerFormat)
            val muxVideoTrack = muxer.addTrack(videoFormat)
            val muxAudioTrack = muxer.addTrack(audioFormat)
            muxer.start()
            muxerStarted = true

            val maxBufSize = maxOf(
                DEFAULT_BUFFER_SIZE,
                safeMaxInputSize(videoFormat),
                safeMaxInputSize(audioFormat)
            )
            var buffer = ByteBuffer.allocateDirect(maxBufSize)
            val bufferInfo = MediaCodec.BufferInfo()

            // Başlangıç PTS hizalaması
            val rawFirstVideoPts = videoExtractor.sampleTime.takeIf { it >= 0L } ?: 0L
            val rawFirstAudioPts = audioExtractor.sampleTime.takeIf { it >= 0L } ?: 0L

            val videoBaseShift: Long
            val audioBaseShift: Long
            when {
                abs(rawFirstVideoPts - rawFirstAudioPts) <= 5_000_000L -> {
                    val commonMin = minOf(rawFirstVideoPts, rawFirstAudioPts)
                    videoBaseShift = -commonMin
                    audioBaseShift = -commonMin
                }
                audioId3StartPtsUs != null && abs(rawFirstVideoPts - audioId3StartPtsUs) <= 5_000_000L -> {
                    val commonMin = minOf(rawFirstVideoPts, audioId3StartPtsUs)
                    videoBaseShift = -commonMin
                    audioBaseShift = (audioId3StartPtsUs - rawFirstAudioPts) - commonMin
                }
                else -> {
                    videoBaseShift = -rawFirstVideoPts
                    audioBaseShift = -rawFirstAudioPts
                }
            }

            var videoDiscDelta = 0L
            var audioDiscDelta = 0L
            var maxVideoPtsWritten = -1L
            var lastAudioPtsWritten = -1L
            var videoSamples = 0L
            var audioSamples = 0L

            var videoEnded = false
            var audioEnded = false

            while (!videoEnded || !audioEnded) {
                if (((videoSamples + audioSamples) and 0xFFL) == 0L) {
                    coroutineContext.ensureActive()
                }

                val rawVPts = if (!videoEnded) videoExtractor.sampleTime else -1L
                if (rawVPts < 0L) videoEnded = true

                val rawAPts = if (!audioEnded) audioExtractor.sampleTime else -1L
                if (rawAPts < 0L) audioEnded = true

                if (videoEnded && audioEnded) break

                val previewVPts = if (!videoEnded) (rawVPts + videoBaseShift + videoDiscDelta).coerceAtLeast(0L) else Long.MAX_VALUE
                val previewAPts = if (!audioEnded) (rawAPts + audioBaseShift + audioDiscDelta).coerceAtLeast(0L) else Long.MAX_VALUE

                if (previewVPts <= previewAPts) {
                    buffer.clear()
                    var sampleSize = videoExtractor.readSampleData(buffer, 0)
                    if (sampleSize < 0 && videoExtractor.sampleTrackIndex >= 0 && buffer.capacity() < 16 * 1024 * 1024) {
                        buffer = ByteBuffer.allocateDirect(buffer.capacity() * 2)
                        sampleSize = videoExtractor.readSampleData(buffer, 0)
                    }
                    if (sampleSize < 0) {
                        videoEnded = true
                        continue
                    }

                    var pts = rawVPts + videoBaseShift + videoDiscDelta
                    if (maxVideoPtsWritten >= 0L) {
                        if (pts < maxVideoPtsWritten - DISCONTINUITY_BACKWARD_THRESHOLD_US ||
                            pts > maxVideoPtsWritten + DISCONTINUITY_FORWARD_THRESHOLD_US
                        ) {
                            val target = maxVideoPtsWritten + DEFAULT_VIDEO_STEP_US
                            videoDiscDelta += (target - pts)
                            pts = target
                        }
                    }
                    if (pts < 0L) pts = 0L
                    if (pts > maxVideoPtsWritten) maxVideoPtsWritten = pts

                    bufferInfo.set(
                        0,
                        sampleSize,
                        pts,
                        toCodecBufferFlags(videoExtractor.sampleFlags)
                    )
                    muxer.writeSampleData(muxVideoTrack, buffer, bufferInfo)
                    videoSamples++
                    videoExtractor.advance()
                } else {
                    buffer.clear()
                    val sampleSize = audioExtractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) {
                        audioEnded = true
                        continue
                    }

                    var pts = rawAPts + audioBaseShift + audioDiscDelta
                    if (lastAudioPtsWritten >= 0L) {
                        if (pts < lastAudioPtsWritten - DISCONTINUITY_BACKWARD_THRESHOLD_US ||
                            pts > lastAudioPtsWritten + DISCONTINUITY_FORWARD_THRESHOLD_US
                        ) {
                            val target = lastAudioPtsWritten + DEFAULT_AUDIO_STEP_US
                            audioDiscDelta += (target - pts)
                            pts = target
                        }
                        if (pts <= lastAudioPtsWritten) {
                            pts = lastAudioPtsWritten + 1L
                        }
                    } else if (pts < 0L) {
                        pts = 0L
                    }
                    lastAudioPtsWritten = pts

                    bufferInfo.set(
                        0,
                        sampleSize,
                        pts,
                        toCodecBufferFlags(audioExtractor.sampleFlags)
                    )
                    muxer.writeSampleData(muxAudioTrack, buffer, bufferInfo)
                    audioSamples++
                    audioExtractor.advance()
                }
            }

            muxer.stop()
            muxerStarted = false
            muxer.release()
            muxer = null

            if (videoSamples == 0L || audioSamples == 0L) {
                tempOutFile.delete()
                return MuxOutcome.CompanionAudioRequired(
                    "Incomplete mux sample count (video=$videoSamples, audio=$audioSamples)"
                )
            }

            if (!verifyContainerTracks(tempOutFile, requireVideo = true, requireAudio = true)) {
                tempOutFile.delete()
                return MuxOutcome.CompanionAudioRequired("Muxed output failed track verification")
            }

            val finalFile = File(outputDir, "$baseName.$outExt")
            if (finalFile.exists() && finalFile.absolutePath != tempOutFile.absolutePath) {
                finalFile.delete()
            }
            if (!tempOutFile.renameTo(finalFile)) {
                tempOutFile.copyTo(finalFile, overwrite = true)
                tempOutFile.delete()
            }

            Log.i(TAG, "Mux succeeded: ${finalFile.name} (videoSamples=$videoSamples, audioSamples=$audioSamples)")
            MuxOutcome.MuxedSingleFile(finalFile, outExt)
        } catch (e: kotlinx.coroutines.CancellationException) {
            tempOutFile?.let { runCatching { it.delete() } }
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "MediaMuxer could not combine video+audio (${e.message}); falling back to companion audio")
            tempOutFile?.let { runCatching { it.delete() } }
            MuxOutcome.CompanionAudioRequired(e.message ?: e.javaClass.simpleName)
        } finally {
            if (muxer != null) {
                if (muxerStarted) runCatching { muxer.stop() }
                runCatching { muxer.release() }
            }
            runCatching { videoExtractor?.release() }
            runCatching { audioExtractor?.release() }
        }
    }

    /**
     * Tek bir MPEG-TS dosyasını (gömülü video + ses içeren HLS akışı) yalnızca HİÇBİR ses izi
     * kaybolmayacaksa standart `.mp4` kapsayıcısına dönüştürür.
     *
     * TS içinde `E-AC-3` / `AC-3` / `DTS` / `MP3` varsa veya `MediaMuxer` ses izini yazamazsa
     * `.ts` dosyasını olduğu gibi korur (`KeepOriginalTs`).
     */
    suspend fun remuxSingleTsToMp4IfLossless(
        tsFile: File,
        outputDir: File,
        baseName: String
    ): SingleTsOutcome {
        val tsInfo = HlsSegmentProcessor.inspectTsFile(tsFile)
        if (!tsInfo.isMpegTs) {
            return SingleTsOutcome.KeepOriginalTs("Not an MPEG-TS stream")
        }
        if (tsInfo.hasNonAacAudio) {
            Log.i(TAG, "TS contains non-AAC audio (${tsInfo.audioStreamTypes}); keeping .ts to preserve audio")
            return SingleTsOutcome.KeepOriginalTs("Non-AAC audio in TS")
        }

        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var tempMp4: File? = null

        return try {
            extractor = MediaExtractor().apply { setDataSource(tsFile.absolutePath) }
            val videoIdx = findFirstTrackIndex(extractor, "video/")
                ?: return SingleTsOutcome.KeepOriginalTs("No video track exposed by MediaExtractor")
            val audioIdx = findFirstTrackIndex(extractor, "audio/")

            if (tsInfo.hasAudio && audioIdx == null) {
                return SingleTsOutcome.KeepOriginalTs("TS has audio in PMT but MediaExtractor did not expose it")
            }

            outputDir.mkdirs()
            tempMp4 = File(outputDir, ".${baseName}_tsremux_tmp.mp4").also {
                if (it.exists()) it.delete()
            }

            val videoFormat = extractor.getTrackFormat(videoIdx)
            muxer = MediaMuxer(tempMp4.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxVideoTrack = muxer.addTrack(videoFormat)
            extractor.selectTrack(videoIdx)

            val muxAudioTrack = if (audioIdx != null) {
                val audioFormat = extractor.getTrackFormat(audioIdx)
                val added = muxer.addTrack(audioFormat)
                extractor.selectTrack(audioIdx)
                added
            } else {
                -1
            }

            muxer.start()
            muxerStarted = true

            val bufCap = maxOf(
                DEFAULT_BUFFER_SIZE,
                safeMaxInputSize(videoFormat),
                if (audioIdx != null) safeMaxInputSize(extractor.getTrackFormat(audioIdx)) else 0
            )
            var buffer = ByteBuffer.allocateDirect(bufCap)
            val bufferInfo = MediaCodec.BufferInfo()

            var baseShift: Long? = null
            var discDelta = 0L
            var maxVideoPts = -1L
            var lastAudioPts = -1L
            var videoSamples = 0L
            var audioSamples = 0L

            while (true) {
                if (((videoSamples + audioSamples) and 0xFFL) == 0L) {
                    coroutineContext.ensureActive()
                }
                val trackIndex = extractor.sampleTrackIndex
                if (trackIndex < 0) break

                buffer.clear()
                var sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0 && buffer.capacity() < 16 * 1024 * 1024) {
                    buffer = ByteBuffer.allocateDirect(buffer.capacity() * 2)
                    sampleSize = extractor.readSampleData(buffer, 0)
                }
                if (sampleSize < 0) break

                val rawPts = extractor.sampleTime.coerceAtLeast(0L)
                if (baseShift == null) {
                    baseShift = -rawPts
                }

                if (trackIndex == videoIdx) {
                    var pts = rawPts + baseShift + discDelta
                    if (maxVideoPts >= 0L && (
                            pts < maxVideoPts - DISCONTINUITY_BACKWARD_THRESHOLD_US ||
                                pts > maxVideoPts + DISCONTINUITY_FORWARD_THRESHOLD_US
                            )
                    ) {
                        val target = maxVideoPts + DEFAULT_VIDEO_STEP_US
                        discDelta += (target - pts)
                        pts = target
                    }
                    if (pts < 0L) pts = 0L
                    if (pts > maxVideoPts) maxVideoPts = pts

                    bufferInfo.set(0, sampleSize, pts, toCodecBufferFlags(extractor.sampleFlags))
                    muxer.writeSampleData(muxVideoTrack, buffer, bufferInfo)
                    videoSamples++
                } else if (trackIndex == audioIdx && muxAudioTrack >= 0) {
                    var pts = rawPts + baseShift + discDelta
                    if (lastAudioPts >= 0L) {
                        if (pts < lastAudioPts - DISCONTINUITY_BACKWARD_THRESHOLD_US ||
                            pts > lastAudioPts + DISCONTINUITY_FORWARD_THRESHOLD_US
                        ) {
                            val target = lastAudioPts + DEFAULT_AUDIO_STEP_US
                            discDelta += (target - pts)
                            pts = target
                        }
                        if (pts <= lastAudioPts) pts = lastAudioPts + 1L
                    } else if (pts < 0L) {
                        pts = 0L
                    }
                    lastAudioPts = pts

                    bufferInfo.set(0, sampleSize, pts, toCodecBufferFlags(extractor.sampleFlags))
                    muxer.writeSampleData(muxAudioTrack, buffer, bufferInfo)
                    audioSamples++
                }

                extractor.advance()
            }

            muxer.stop()
            muxerStarted = false
            muxer.release()
            muxer = null

            val expectAudio = tsInfo.hasAudio || audioIdx != null
            if (videoSamples == 0L || (expectAudio && audioSamples == 0L)) {
                tempMp4.delete()
                return SingleTsOutcome.KeepOriginalTs(
                    "Remux lost samples (video=$videoSamples, audio=$audioSamples, expectAudio=$expectAudio)"
                )
            }

            if (tempMp4.length() < tsFile.length() / 3L) {
                tempMp4.delete()
                return SingleTsOutcome.KeepOriginalTs("Remuxed MP4 unexpectedly small")
            }

            if (!verifyContainerTracks(tempMp4, requireVideo = true, requireAudio = expectAudio)) {
                tempMp4.delete()
                return SingleTsOutcome.KeepOriginalTs("Remuxed MP4 failed track verification")
            }

            val finalMp4 = File(outputDir, "$baseName.mp4")
            if (finalMp4.exists() && finalMp4.absolutePath != tempMp4.absolutePath) {
                finalMp4.delete()
            }
            if (!tempMp4.renameTo(finalMp4)) {
                tempMp4.copyTo(finalMp4, overwrite = true)
                tempMp4.delete()
            }
            SingleTsOutcome.RemuxedToMp4(finalMp4)
        } catch (e: kotlinx.coroutines.CancellationException) {
            tempMp4?.let { runCatching { it.delete() } }
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Single-TS remux skipped (${e.message}); keeping sanitized .ts")
            tempMp4?.let { runCatching { it.delete() } }
            SingleTsOutcome.KeepOriginalTs(e.message ?: e.javaClass.simpleName)
        } finally {
            if (muxer != null) {
                if (muxerStarted) runCatching { muxer.stop() }
                runCatching { muxer.release() }
            }
            runCatching { extractor?.release() }
        }
    }

    private fun verifyContainerTracks(file: File, requireVideo: Boolean, requireAudio: Boolean): Boolean {
        if (!file.isFile || file.length() == 0L) return false
        var verifier: MediaExtractor? = null
        return try {
            verifier = MediaExtractor().apply { setDataSource(file.absolutePath) }
            val hasVideo = findFirstTrackIndex(verifier, "video/") != null
            val hasAudio = findFirstTrackIndex(verifier, "audio/") != null
            (!requireVideo || hasVideo) && (!requireAudio || hasAudio)
        } catch (_: Exception) {
            false
        } finally {
            runCatching { verifier?.release() }
        }
    }

    private fun findFirstTrackIndex(extractor: MediaExtractor, mimePrefix: String): Int? {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith(mimePrefix, ignoreCase = true)) {
                return i
            }
        }
        return null
    }

    private fun safeMaxInputSize(format: MediaFormat): Int {
        return runCatching {
            if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
            } else {
                0
            }
        }.getOrDefault(0)
    }

    private fun toCodecBufferFlags(extractorFlags: Int): Int {
        var flags = 0
        if ((extractorFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
            flags = flags or MediaCodec.BUFFER_FLAG_KEY_FRAME
        }
        return flags
    }
}
