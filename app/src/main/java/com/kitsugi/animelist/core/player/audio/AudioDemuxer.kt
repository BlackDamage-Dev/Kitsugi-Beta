package com.kitsugi.animelist.core.player.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.nio.ByteBuffer
import java.util.Locale

/** Exports only the selected demux track; never writes a video container with its video still in it. */
internal object AudioDemuxer {
    data class Result(val extension: String, val mime: String)
    private const val MAX_SAMPLE = 8 * 1024 * 1024

    suspend fun export(context: Context, source: String, headers: Map<String, String>,
                       language: String?, output: File, finiteManifest: Boolean,
                       independentAudio: Boolean, requireKnownLanguage: Boolean = false): Result {
        var extractor = MediaExtractor()
        var localCopy: File? = null
        try {
            val uri = Uri.parse(source)
            when (uri.scheme?.lowercase(Locale.ROOT)) {
                "http", "https" -> {
                    try {
                        extractor.setDataSource(source, headers.filterKeys { !it.equals("Range", true) })
                    } catch (first: Exception) {
                        // Some hosts accept app OkHttp/cookies but not Android's native HTTP stack.
                        // A *bounded*, seekable local copy is a last resort; never publish it as audio.
                        extractor.release()
                        val copy = File.createTempFile("audio-source-", ".part", context.cacheDir)
                        localCopy = copy
                        copy.outputStream().buffered().use { out ->
                            AudioExportHttp.append(source, source, headers, null, out, copy.parentFile!!, 0)
                        }
                        extractor = MediaExtractor()
                        try { extractor.setDataSource(copy.absolutePath) }
                        catch (second: Exception) { second.addSuppressed(first); throw second }
                    }
                }
                "file", "content" -> extractor.setDataSource(context, uri, null)
                null -> {
                    require(source.startsWith('/')) { "Yerel ses yolu geçersiz" }
                    extractor.setDataSource(source)
                }
                else -> error("Bu kaynak türünden ses indirilemiyor")
            }
            val candidates = (0 until extractor.trackCount).mapNotNull { index ->
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (!mime.startsWith("audio/")) null else {
                    AudioExportSpec.Candidate(
                        index = index,
                        language = format.getString(MediaFormat.KEY_LANGUAGE).orEmpty()
                    )
                }
            }
            val track = AudioExportSpec.select(candidates, language, requireKnownLanguage)
            val format = extractor.getTrackFormat(track)
            require(finiteManifest || format.containsKey(MediaFormat.KEY_DURATION) && format.getLong(MediaFormat.KEY_DURATION) > 0) {
                "Süresiz/canlı ses dosyası oluşturulamıyor"
            }
            extractor.selectTrack(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: error("Ses kodeği bilinmiyor")
            if (independentAudio && mime in setOf("audio/flac", "audio/raw", "audio/wav")) {
                try {
                    return StandaloneAudio.copy(context, localCopy?.absolutePath ?: source, headers, output)
                } catch (cancel: kotlinx.coroutines.CancellationException) {
                    throw cancel
                } catch (_: Exception) {
                    // A container may contain FLAC/PCM without being a standalone file.
                    output.delete()
                }
            }
            val result = when (mime) {
                "audio/mp4a-latm" -> Result("m4a", "audio/mp4")
                "audio/vorbis", "audio/opus" -> Result("webm", "audio/webm")
                "audio/mpeg" -> Result("mp3", "audio/mpeg")
                "audio/ac3" -> Result("ac3", "audio/ac3")
                "audio/eac3" -> Result("eac3", "audio/eac3")
                else -> Result("wav", "audio/wav")
            }
            if (result.extension == "wav" || mime == "audio/opus" && Build.VERSION.SDK_INT < 29) {
                AudioPcmExport.convert(extractor, format, output)
                return Result("wav", "audio/wav")
            }
            if (result.extension in setOf("mp3", "ac3", "eac3")) {
                writeSelfFramed(extractor, result.extension, output)
                return result
            }
            val container = if (result.extension == "m4a") MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
                else MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
            var muxer: MediaMuxer? = null
            var started = false
            var outputTrack = -1
            try {
                try {
                    muxer = MediaMuxer(output.absolutePath, container)
                    outputTrack = muxer.addTrack(format)
                    muxer.start()
                    started = true
                } catch (e: Exception) {
                    runCatching { muxer?.release() }
                    muxer = null
                    // Unsupported container/codec on this Android version: decoder-dependent WAV.
                    AudioPcmExport.convert(extractor, format, output)
                    return Result("wav", "audio/wav")
                }
                val buffer = ByteBuffer.allocateDirect(MAX_SAMPLE)
                val sample = MediaCodec.BufferInfo()
                var firstTime = -1L
                var lastTime = -1L
                var count = 0
                while (extractor.sampleTrackIndex >= 0) {
                    currentCoroutineContext().ensureActive()
                    require(extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED == 0) { "DRM/şifreli ses çıkarılamıyor" }
                    require(extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME == 0) { "Eksik ses örneği" }
                    if (Build.VERSION.SDK_INT >= 28) require(extractor.sampleSize in 0..MAX_SAMPLE.toLong()) { "Ses örneği çok büyük" }
                    buffer.clear()
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) break
                    require(size in 1 until MAX_SAMPLE) { "Ses örneği boş veya çok büyük" }
                    val time = extractor.sampleTime
                    require(time >= 0 && (lastTime < 0 || time >= lastTime)) { "Ses zaman damgası kesintili" }
                    if (firstTime < 0) firstTime = time
                    lastTime = time
                    sample.set(0, size, time - firstTime,
                        if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                    buffer.position(0)
                    buffer.limit(size)
                    muxer.writeSampleData(outputTrack, buffer, sample)
                    count++
                    extractor.advance()
                }
                require(count > 0) { "Ses örneği okunamadı" }
                muxer.stop()
                started = false
                require(output.length() > 0) { "Ses kapsayıcısı boş" }
                return result
            } finally {
                if (started) runCatching { muxer?.stop() }
                runCatching { muxer?.release() }
            }
        } finally {
            runCatching { extractor.release() }
            localCopy?.delete()
        }
    }

    /** Raw MP3/AC3 access units are self-framed. Reject MP4/TS units without their frame sync. */
    private suspend fun writeSelfFramed(extractor: MediaExtractor, extension: String, output: File) {
        val buffer = ByteBuffer.allocate(MAX_SAMPLE)
        var count = 0
        var written = 0L
        output.outputStream().buffered().use { out ->
            while (extractor.sampleTrackIndex >= 0) {
                currentCoroutineContext().ensureActive()
                require(extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED == 0) { "Şifreli ses çıkarılamıyor" }
                require(extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME == 0) { "Eksik ses örneği" }
                if (Build.VERSION.SDK_INT >= 28) require(extractor.sampleSize in 0..MAX_SAMPLE.toLong()) { "Ses örneği çok büyük" }
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                require(size in 2 until MAX_SAMPLE) { "Ses örneği bozuk" }
                val a = buffer.get(0).toInt() and 0xff
                val b = buffer.get(1).toInt() and 0xff
                require(if (extension == "mp3") a == 0xff && b and 0xe0 == 0xe0 else a == 0x0b && b == 0x77) {
                    "Ses örneğinde kare başlığı yok; ham akış güvenle yazılamıyor"
                }
                require(written + size <= AudioExportHttp.MAX_TEMP_BYTES) { "Ses dosyası çok büyük" }
                out.write(buffer.array(), 0, size)
                written += size
                count++
                extractor.advance()
            }
        }
        require(count > 0 && output.length() > 0) { "Ses örneği bulunamadı" }
    }
}
