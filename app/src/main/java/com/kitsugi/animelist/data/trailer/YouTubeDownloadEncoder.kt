package com.kitsugi.animelist.data.trailer

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.util.Log
import com.kitsugi.animelist.core.download.MuxSampleGuard
import java.io.File
import java.nio.ByteBuffer

/**
 * YouTube'un ayrı video ve ses akışlarını tek dosyada birleştirir.
 *
 * NEDEN GEREKLİ?
 * YouTube 720p ve üzerinde video ile sesi ayrı akış olarak dağıtır. Bunları ayrı ayrı
 * kaydetmek kullanıcı için işe yaramaz (video sessiz oynar). Media3 oynatıcı ikisini
 * `MergingMediaSource` ile birleştirir; indirme tarafında aynı işi `MediaExtractor` +
 * `MediaMuxer` ile yapıyoruz. Yeniden kodlama YOK: yalnızca kapsayıcı (container) yazılır,
 * bu yüzden işlem hızlı ve kayıpsızdır.
 *
 * Kapsayıcı seçimi:
 *  - H.264/HEVC video → `.mp4` (ses AAC ise; değilse birleştirme denenmez)
 *  - VP8/VP9 video → `.webm` (ses Vorbis ise; Opus MediaMuxer'da garanti değil)
 * Uyumsuz kombinasyonlarda `false` döner ve çağıran taraf progressive akışa düşer.
 */
object YouTubeDownloadEncoder {

    private const val TAG = "YouTubeDownloadEncoder"

    /**
     * @param videoFile indirilmiş video akışı (kendi kapsayıcısıyla)
     * @param audioFile indirilmiş ses akışı ya da `null` (ses yoksa video kopyalanır)
     * @param output    yazılacak dosya — uzantısı burada düzeltilir
     * @return birleştirme başarılı mı?
     */
    fun mux(videoFile: File, audioFile: File?, output: File): Boolean {
        if (!videoFile.exists() || videoFile.length() == 0L) return false
        if (audioFile == null || !audioFile.exists() || audioFile.length() == 0L) {
            // Ses yok: video akışını olduğu gibi taşı.
            return runCatching { videoFile.copyTo(output, overwrite = true) }.isSuccess
        }

        var videoExtractor: MediaExtractor? = null
        var audioExtractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        return try {
            videoExtractor = MediaExtractor().apply { setDataSource(videoFile.absolutePath) }
            audioExtractor = MediaExtractor().apply { setDataSource(audioFile.absolutePath) }

            val videoTrack = firstTrack(videoExtractor, "video/") ?: return false
            val audioTrack = firstTrack(audioExtractor, "audio/") ?: return false

            val videoFormat = videoExtractor.getTrackFormat(videoTrack)
            val audioFormat = audioExtractor.getTrackFormat(audioTrack)
            val videoMime = videoFormat.getString(MediaFormat.KEY_MIME).orEmpty()
            val audioMime = audioFormat.getString(MediaFormat.KEY_MIME).orEmpty()

            val container = chooseContainer(videoMime, audioMime) ?: run {
                Log.w(TAG, "Uyumsuz kodek çifti: video=$videoMime ses=$audioMime")
                return false
            }

            videoExtractor.selectTrack(videoTrack)
            audioExtractor.selectTrack(audioTrack)

            // Dosya adını kapsayıcıya göre düzelt (.mp4 ↔ .webm).
            val target = withExtension(output, container.extension)
            if (target.exists()) target.delete()

            muxer = MediaMuxer(target.absolutePath, container.muxerOutput)
            val outVideoTrack = muxer.addTrack(videoFormat)
            val outAudioTrack = muxer.addTrack(audioFormat)
            muxer.start()
            muxerStarted = true

            val videoSamples = copyTrack(
                extractor = videoExtractor,
                muxer = muxer,
                outTrack = outVideoTrack,
                stepUs = MuxSampleGuard.DEFAULT_VIDEO_STEP_US,
                isAudio = false
            )
            val audioSamples = copyTrack(
                extractor = audioExtractor,
                muxer = muxer,
                outTrack = outAudioTrack,
                stepUs = MuxSampleGuard.DEFAULT_AUDIO_STEP_US,
                isAudio = true
            )

            if (videoSamples <= 0L || audioSamples <= 0L) {
                Log.w(TAG, "Birleştirmede yazılabilir örnek yok (video=$videoSamples ses=$audioSamples)")
                runCatching { muxer.release() }
                muxer = null
                runCatching { target.delete() }
                return false
            }

            // stop() TEK kez ve tüm yazmalar bittikten sonra çağrılır; başarısızsa moov
            // yarım kalır ve dosya oynatılamaz → birleştirme başarısız sayılır.
            val stopped = runCatching { muxer.stop() }.isSuccess
            muxerStarted = false
            runCatching { muxer.release() }
            muxer = null
            if (!stopped) {
                Log.w(TAG, "muxer.stop() başarısız — birleştirme geçersiz, dosya siliniyor")
                runCatching { target.delete() }
                return false
            }

            if (target.exists() && target.length() > 0L) {
                if (target != output) {
                    runCatching { target.copyTo(output, overwrite = true) }
                    runCatching { target.delete() }
                }
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Birleştirme başarısız: ${e.message}", e)
            // Yarım kalan muxer `stop()` edilmeden bırakılır: yarım moov zaten geçersizdir ve
            // ikinci bir stop() muxer'ı tanımsız durumda bırakabilir. Çıktı geçersiz sayılır
            // (çağıran taraf progressive akışa düşer).
            runCatching { muxer?.release() }
            muxer = null
            false
        } finally {
            runCatching { videoExtractor?.release() }
            runCatching { audioExtractor?.release() }
            if (muxer != null) {
                // Yalnızca hiç stop() denenmemişse bir kez dene (çift stop muxer'ı bozar).
                if (muxerStarted) runCatching { muxer?.stop() }
                runCatching { muxer?.release() }
            }
        }
    }

    private class Container(val extension: String, val muxerOutput: Int)

    private fun chooseContainer(videoMime: String, audioMime: String): Container? {
        val mp4Compatible = videoMime in setOf(
            MediaFormat.MIMETYPE_VIDEO_AVC,
            MediaFormat.MIMETYPE_VIDEO_HEVC
        ) && audioMime == MediaFormat.MIMETYPE_AUDIO_AAC
        if (mp4Compatible) return Container("mp4", MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        val webmCompatible = videoMime in setOf(
            MediaFormat.MIMETYPE_VIDEO_VP8,
            MediaFormat.MIMETYPE_VIDEO_VP9,
            MediaFormat.MIMETYPE_VIDEO_AV1
        ) && audioMime == MediaFormat.MIMETYPE_AUDIO_VORBIS
        if (webmCompatible && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Container("webm", MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM)
        }
        return null
    }

    private fun withExtension(file: File, extension: String): File {
        val base = file.name.substringBeforeLast('.', file.name)
        return File(file.parentFile, "$base.$extension")
    }

    private fun firstTrack(extractor: MediaExtractor, prefix: String): Int? {
        for (index in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith(prefix)) return index
        }
        return null
    }

    /**
     * Bir izi olduğu gibi (yeniden kodlamadan) muxer'a kopyalar.
     *
     * Eski sürüm `readSampleData()` sonucunu doğrulamadan `BufferInfo.size`'a yazıyordu;
     * `0`/negatif boyut ve negatif PTS `MPEG4Writer`'da `size_t(-1)` yazımına ve
     * `FORTIFY: write: count 18446744073709551615` → `SIGABRT` çökmesine yol açabiliyor
     * (2026-11-20). Ayrıca ses izine `sampleFlags` (anahtar kare) olduğu gibi aktarılıyordu.
     *
     * @return yazılan örnek sayısı
     */
    private fun copyTrack(
        extractor: MediaExtractor,
        muxer: MediaMuxer,
        outTrack: Int,
        stepUs: Long,
        isAudio: Boolean
    ): Long {
        val capacity = runCatching {
            extractor.getTrackFormat(extractor.sampleTrackIndex.takeIf { it >= 0 } ?: 0)
                .getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
        }.getOrDefault(0).coerceIn(1 shl 20, 8 shl 20)
        val buffer = ByteBuffer.allocateDirect(capacity)
        val info = MediaCodec.BufferInfo()
        var written = 0L
        var lastPtsUs = -1L
        var skipped = 0L
        while (true) {
            buffer.clear()
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) break
            if (!MuxSampleGuard.isWritableSampleSize(size) || size > buffer.capacity()) {
                skipped++
                if (!extractor.advance()) break
                continue
            }
            val pts = MuxSampleGuard.nextMonotonicPtsUs(
                rawPtsUs = extractor.sampleTime,
                lastWrittenUs = lastPtsUs,
                stepUs = stepUs
            )
            lastPtsUs = pts
            info.offset = 0
            info.size = size
            info.presentationTimeUs = pts
            info.flags = if (isAudio) {
                MuxSampleGuard.audioSampleFlags()
            } else {
                MuxSampleGuard.videoSampleFlags(
                    sourceFlags = extractor.sampleFlags,
                    keyFrameBit = MediaExtractor.SAMPLE_FLAG_SYNC,
                    codecKeyFrame = MediaCodec.BUFFER_FLAG_KEY_FRAME
                )
            }
            muxer.writeSampleData(outTrack, buffer, info)
            written++
            if (!extractor.advance()) break
        }
        if (skipped > 0L) {
            Log.w(TAG, "Geçersiz örnek atlandı: $skipped (iz=${if (isAudio) "ses" else "video"})")
        }
        return written
    }
}
