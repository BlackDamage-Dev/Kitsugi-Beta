package com.kitsugi.animelist.core.download.video

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
 * Ses ve görüntüsü **ayrı akışlarda** sunulan kaynakları tek dosyada birleştirir.
 *
 * Neden gerekli: YouTube (ve bazı eklentiler) bir videoyu video-only + audio-only olarak
 * veriyor. Uygulama uzun süre bu tür adreslerde "tek dosya olarak indirilemiyor" diyerek
 * indirmeyi reddediyordu. MediaMuxer, yeniden kodlama yapmadan (transcode yok, hızlı ve
 * kayıpsız) iki bit akışını tek bir MP4/WebM kabına yazabiliyor.
 *
 * ## 2026-11-20 çökmesi ve bu dosyadaki düzeltmeler
 * `crash_history` günlüğünde süreç, `MPEG4Writer` iş parçacığında
 * `FORTIFY: write: count 18446744073709551615 > SSIZE_MAX` → `SIGABRT` ile öldü.
 * Bu, `writeSampleData` çağrısına **-1 bayt** boyut verilmesinin doğrudan sonucudur.
 * Eski kod `readSampleData()` sonucunu doğrulamadan yazıyordu:
 *
 * ```kotlin
 * val size = videoExtractor.readSampleData(videoBuffer, 0)
 * if (size <= 0 || videoTimeUs < 0L) { videoDone = true; continue }   // eski koruma
 * info.set(0, size, videoTimeUs, videoExtractor.sampleFlags)
 * activeMuxer.writeSampleData(outVideoTrack, videoBuffer, info)
 * ```
 *
 * …ancak `writeSampleData` `size`'ı `buffer.limit()`'ten değil `BufferInfo.size`'tan alır ve
 * negatif/aşırı değerler `catch (t: Throwable)` ile **yakalanamaz** — çökme native yazıcı
 * iş parçacığında olur. Şimdi her örnek [MuxSampleGuard] üzerinden geçiyor:
 *  - boyut `1..MAX_SAMPLE_BYTES` dışındaysa örnek yazılmaz,
 *  - PTS negatif/absürt ise kırpılır ve iz başına kesin artan olacak şekilde zorlanır,
 *  - ses izlerine `BUFFER_FLAG_KEY_FRAME` verilmez.
 *
 * ## Muxer yaşam döngüsü
 * Eski kod `stop()` başarısız olduğunda bile `release()` çağırıyor ve yarım moov'lu bir
 * dosyayı geçerli sanabiliyordu. Artık `stop()` **tek** kez, yalnızca tüm örnekler
 * yazıldıktan sonra çağrılır; başarısızsa çıktı silinir ve `false` döner.
 *
 * Notlar:
 *  - MP4 çıktısı H.264 + AAC için her cihazda güvenli; VP9/Opus için WebM çıktısı deniyoruz.
 *  - Birleştirme başarısız olursa çağıran taraf iki dosyayı ayrı ayrı saklar; kullanıcı
 *    hiçbir zaman "indirme sessizce kayboldu" durumuyla karşılaşmaz.
 */
internal object MediaStreamMuxer {

    private const val TAG = "MediaStreamMuxer"

    /** Kaba bir üst sınır: bu sayıdan fazla örnek yazıldıysa akış zaten bozuktur. */
    private const val MAX_SAMPLES_PER_TRACK = 40_000_000L

    fun mux(
        videoFile: File,
        audioFile: File,
        outputFile: File,
        preferWebm: Boolean = false
    ): Boolean {
        if (!videoFile.isUsable() || !audioFile.isUsable()) return false
        // MUXER_OUTPUT_WEBM API 28+;'de mevcut. minSdk 26 olduğu için API 26-27 cihazlarda
        // WebM denemesi atlanır; yalnızca MP4 denir.
        val webmSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
        val attempts = buildList {
            add(false) // MP4 her zaman ilk deneme (en uyumlu)
            if (webmSupported && preferWebm) add(true) // WebM yalnızca desteklenip istendiğinde
        }
        for (webm in attempts) {
            runCatching { if (outputFile.exists()) outputFile.delete() }
            if (tryMux(videoFile, audioFile, outputFile, webm)) return true
            runCatching { if (outputFile.exists()) outputFile.delete() }
        }
        Log.w(TAG, "Muxing failed for ${videoFile.name} + ${audioFile.name}")
        return false
    }

    private fun tryMux(
        videoFile: File,
        audioFile: File,
        outputFile: File,
        webm: Boolean
    ): Boolean {
        val videoExtractor = MediaExtractor()
        val audioExtractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var muxerStopped = false
        var skippedVideo = 0L
        var skippedAudio = 0L
        return try {
            videoExtractor.setDataSource(videoFile.absolutePath)
            audioExtractor.setDataSource(audioFile.absolutePath)

            val videoTrack = firstTrackOfType(videoExtractor, "video/")
            val audioTrack = firstTrackOfType(audioExtractor, "audio/")
            if (videoTrack < 0 || audioTrack < 0) {
                Log.w(TAG, "Missing track (video=$videoTrack audio=$audioTrack)")
                return false
            }

            val videoFormat = videoExtractor.getTrackFormat(videoTrack)
            val audioFormat = audioExtractor.getTrackFormat(audioTrack)

            val activeMuxer = MediaMuxer(
                outputFile.absolutePath,
                if (webm) MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
                else MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            )
            muxer = activeMuxer

            val outVideoTrack = activeMuxer.addTrack(videoFormat)
            val outAudioTrack = activeMuxer.addTrack(audioFormat)
            val maxBuffer = maxOf(
                maxInputSize(videoFormat),
                maxInputSize(audioFormat),
                1 shl 16
            )

            activeMuxer.start()
            muxerStarted = true

            videoExtractor.selectTrack(videoTrack)
            audioExtractor.selectTrack(audioTrack)
            runCatching { videoExtractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC) }
            runCatching { audioExtractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC) }

            val videoBuffer = ByteBuffer.allocateDirect(maxBuffer)
            val audioBuffer = ByteBuffer.allocateDirect(maxBuffer)
            val info = MediaCodec.BufferInfo()

            var videoTimeUs = videoExtractor.sampleTime
            var audioTimeUs = audioExtractor.sampleTime
            var videoDone = videoTimeUs < 0L
            var audioDone = audioTimeUs < 0L

            var lastVideoPtsWritten = -1L
            var lastAudioPtsWritten = -1L
            var videoSamples = 0L
            var audioSamples = 0L

            while ((!videoDone || !audioDone) &&
                videoSamples < MAX_SAMPLES_PER_TRACK &&
                audioSamples < MAX_SAMPLES_PER_TRACK
            ) {
                val useVideo = when {
                    videoDone -> false
                    audioDone -> true
                    else -> videoTimeUs <= audioTimeUs
                }
                if (useVideo) {
                    videoBuffer.clear()
                    val size = videoExtractor.readSampleData(videoBuffer, 0)
                    if (!MuxSampleGuard.isWritableSampleSize(size)) {
                        // Akış bitti (size < 0) ya da örnek okunamadı (size == 0):
                        // YAZMA — negatif boyut MPEG4Writer'da SIGABRT üretir.
                        if (size < 1) {
                            videoDone = true
                        } else {
                            skippedVideo++
                            videoExtractor.advance()
                        }
                        continue
                    }
                    val pts = MuxSampleGuard.nextMonotonicPtsUs(
                        rawPtsUs = videoTimeUs,
                        lastWrittenUs = lastVideoPtsWritten,
                        stepUs = MuxSampleGuard.DEFAULT_VIDEO_STEP_US
                    )
                    lastVideoPtsWritten = pts
                    info.set(
                        0,
                        size,
                        pts,
                        MuxSampleGuard.videoSampleFlags(
                            sourceFlags = videoExtractor.sampleFlags,
                            keyFrameBit = MediaExtractor.SAMPLE_FLAG_SYNC,
                            codecKeyFrame = MediaCodec.BUFFER_FLAG_KEY_FRAME
                        )
                    )
                    activeMuxer.writeSampleData(outVideoTrack, videoBuffer, info)
                    videoSamples++
                    val advanced = videoExtractor.advance()
                    videoTimeUs = if (advanced) videoExtractor.sampleTime else -1L
                    if (videoTimeUs < 0L) videoDone = true
                } else {
                    audioBuffer.clear()
                    val size = audioExtractor.readSampleData(audioBuffer, 0)
                    if (!MuxSampleGuard.isWritableSampleSize(size)) {
                        if (size < 1) {
                            audioDone = true
                        } else {
                            skippedAudio++
                            audioExtractor.advance()
                        }
                        continue
                    }
                    val pts = MuxSampleGuard.nextMonotonicPtsUs(
                        rawPtsUs = audioTimeUs,
                        lastWrittenUs = lastAudioPtsWritten,
                        stepUs = MuxSampleGuard.DEFAULT_AUDIO_STEP_US
                    )
                    lastAudioPtsWritten = pts
                    // Ses örneklerinde anahtar kare kavramı YOK — bayrak her zaman 0.
                    info.set(0, size, pts, MuxSampleGuard.audioSampleFlags())
                    activeMuxer.writeSampleData(outAudioTrack, audioBuffer, info)
                    audioSamples++
                    val advanced = audioExtractor.advance()
                    audioTimeUs = if (advanced) audioExtractor.sampleTime else -1L
                    if (audioTimeUs < 0L) audioDone = true
                }
            }

            if (skippedVideo > 0L || skippedAudio > 0L) {
                Log.w(TAG, "Bozuk örnekler atlandı (video=$skippedVideo ses=$skippedAudio) — çökme önlendi")
            }

            if (videoSamples <= 0L || audioSamples <= 0L) {
                Log.w(TAG, "Yazılabilir örnek yok (video=$videoSamples ses=$audioSamples)")
                return false
            }

            // stop() TEK kez, tüm yazmalar bittikten sonra. Başarısızsa moov yarım kalır.
            muxerStopped = runCatching { activeMuxer.stop() }.isSuccess
            // stop() denendikten sonra tekrar denenmez: ikinci stop() çağrısı muxer'ı
            // tanımsız durumda bırakır. Bu yüzden bayrak başarıdan bağımsız kapatılır.
            muxerStarted = false
            if (!muxerStopped) {
                Log.w(TAG, "muxer.stop() başarısız — çıktı geçersiz sayılıyor (webm=$webm)")
                return false
            }
            outputFile.length() > 0L
        } catch (t: Throwable) {
            Log.w(TAG, "Mux attempt failed (webm=$webm): ${t.message}")
            false
        } finally {
            if (muxer != null) {
                if (muxerStarted) runCatching { muxer?.stop() }
                runCatching { muxer?.release() }
            }
            runCatching { videoExtractor.release() }
            runCatching { audioExtractor.release() }
        }
    }

    private fun firstTrackOfType(extractor: MediaExtractor, mimePrefix: String): Int {
        for (index in 0 until extractor.trackCount) {
            val mime = runCatching { extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME) }
                .getOrNull()
                .orEmpty()
            if (mime.startsWith(mimePrefix, ignoreCase = true)) return index
        }
        return -1
    }

    private fun maxInputSize(format: MediaFormat): Int =
        runCatching { format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) }
            .getOrDefault(0)
            .coerceIn(1 shl 16, 4 shl 20)

    private fun File.isUsable(): Boolean = exists() && length() > 0L
}
