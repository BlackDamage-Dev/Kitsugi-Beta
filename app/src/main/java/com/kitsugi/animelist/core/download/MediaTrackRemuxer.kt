package com.kitsugi.animelist.core.download

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.extractor.DefaultExtractorInput
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.DiscardingTrackOutput
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.text.SubtitleParser
import com.kitsugi.animelist.data.model.ExternalAudioTrack
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

/**
 * Kayıpsız (re-encode yapmadan) medya birleştirme (remux/mux) ve bağımsız ses ayıklama motoru.
 *
 * Neden `android.media.MediaExtractor` yerine Media3 `Extractor` kullanılıyor?
 * - Android'in yerleşik `MediaExtractor`'ı MPEG-TS segmentlerinde bir PES paketindeki çoklu AAC
 *   karelerine aynı PTS zaman damgasını atayabildiği veya `csd-0` başlığını atladığı için
 *   `MediaMuxer` ses örneklerinin büyük kısmını düşürebilir ya da çöker.
 * - Media3'ün `TsExtractor` / `FragmentedMp4Extractor` / `MatroskaExtractor` altyapısı her bir
 *   AAC / E-AC-3 / AC-3 / Opus / MP3 / FLAC erişim birimini ayrı zaman damgası ve tam `initializationData`
 *   ile çıkarır; ayrıca sahte resim başlıklı TS segmentleri ve tüm modern ses kodekleriyle çalışır.
 */
object MediaTrackRemuxer {

    private const val TAG = "MediaTrackRemuxer"

    data class ProbedMediaTrack(
        val extractorTrackId: Int,
        val audioOrderIndex: Int,
        val isAudio: Boolean,
        val isVideo: Boolean,
        val sampleMimeType: String,
        val codecs: String,
        val language: String,
        val label: String,
        val channelCount: Int,
        val sampleRate: Int,
        val bitrate: Int,
        val width: Int,
        val height: Int,
        val initializationData: List<ByteArray>,
        val media3Format: Format? = null
    ) {
        val normalizedLang: String
            get() = ExternalAudioTrack.normalizeLangCode(
                language.ifBlank { ExternalAudioTrack.inferLanguageFromText(label) }
            )
    }

    data class AudioExtractionResult(
        val outputFile: File,
        val extension: String,
        val mimeType: String,
        val sampleCount: Long
    )

    data class VideoRemuxResult(
        val outputFile: File,
        val extension: String,
        val audioMergedIntoContainer: Boolean,
        val companionAudioFiles: List<CompanionAudioResult> = emptyList()
    )

    data class CompanionAudioResult(
        val file: File,
        val lang: String,
        val label: String,
        val extension: String
    )

    /**
     * Dosyadaki tüm ses ve görüntü izlerini tüm dosyayı belleğe almadan hızlıca (~birkaç MB okuyarak) tespit eder.
     * `moov` atomu dosyanın sonunda yer alan non-faststart `.mp4` / `.m4a` dosyalarında da `RESULT_SEEK`
     * takibiyle iz bilgisini eksiksiz okur.
     */
    fun probeTracks(file: File): List<ProbedMediaTrack> {
        if (!file.exists() || file.length() <= 0L) return emptyList()
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val extractor = selectMatchingExtractor(raf) ?: return emptyList()
                try {
                    val probeOutput = ProbingExtractorOutput()
                    extractor.init(probeOutput)
                    var input = createExtractorInput(raf, 0L)
                    var windowStart = 0L
                    var seeks = 0
                    val holder = PositionHolder()
                    var steps = 0
                    while (steps < 3000) {
                        steps++
                        if (probeOutput.isReady()) break
                        val res = extractor.read(input, holder)
                        if (res == Extractor.RESULT_SEEK) {
                            seeks++
                            if (seeks > 12 || holder.position < 0L || holder.position >= raf.length()) {
                                break
                            }
                            windowStart = holder.position
                            input = createExtractorInput(raf, holder.position)
                            continue
                        }
                        if (res == Extractor.RESULT_END_OF_INPUT) break
                        if ((input.position - windowStart) > 6L * 1024L * 1024L && probeOutput.tracksEnded) break
                    }
                    collectProbedTracks(probeOutput)
                } finally {
                    runCatching { extractor.release() }
                }
            }
        }.getOrElse { e ->
            Log.w(TAG, "probeTracks hatası (${file.name}): ${e.message}")
            emptyList()
        }
    }

    /**
     * Uzak HTTP(S) konteyner akışındaki (`.mkv`, `.mp4`, `.webm`, `.ts`, `.avi`, `.flv`) gömülü
     * tüm ses izlerini tüm videoyu indirmeden HTTP `Range` pencereleriyle (~1.5 MB) tespit eder.
     */
    fun probeRemoteContainerTracks(
        httpClient: StreamDownloadHttpClient,
        url: String,
        extraHeaders: Map<String, String> = emptyMap()
    ): List<ProbedMediaTrack> {
        return runCatching {
            val firstWindow = httpClient.fetchRangeWindowBlocking(
                url = url,
                extraHeaders = extraHeaders,
                startOffset = 0L,
                maxBytes = 1_572_864
            ) ?: return emptyList()
            if (firstWindow.bytes.isEmpty()) return emptyList()

            val windowCache = ArrayList<StreamDownloadHttpClient.RangeWindow>(4).apply {
                add(firstWindow)
            }
            var totalLength = firstWindow.totalResourceLength

            fun createRemoteInput(position: Long): DefaultExtractorInput? {
                if (position < 0L) return null
                if (totalLength > 0L && position >= totalLength) return null
                var cursor = position
                val reader = DataReader { buffer, offset, length ->
                    if (length <= 0) return@DataReader 0
                    if (totalLength > 0L && cursor >= totalLength) return@DataReader C.RESULT_END_OF_INPUT
                    var activeWin = windowCache.firstOrNull { w ->
                        cursor >= w.startOffset && cursor < (w.startOffset + w.bytes.size)
                    }
                    if (activeWin == null && windowCache.size < 5) {
                        val fetched = httpClient.fetchRangeWindowBlocking(
                            url = url,
                            extraHeaders = extraHeaders,
                            startOffset = cursor,
                            maxBytes = 1_572_864
                        )
                        if (fetched != null && fetched.bytes.isNotEmpty() && fetched.startOffset == cursor) {
                            if (fetched.totalResourceLength > 0L) {
                                totalLength = fetched.totalResourceLength
                            }
                            windowCache.add(fetched)
                            activeWin = fetched
                        }
                    }
                    val win = activeWin ?: return@DataReader C.RESULT_END_OF_INPUT
                    val rel = (cursor - win.startOffset).toInt()
                    val avail = win.bytes.size - rel
                    if (avail <= 0) return@DataReader C.RESULT_END_OF_INPUT
                    val toCopy = minOf(length, avail)
                    System.arraycopy(win.bytes, rel, buffer, offset, toCopy)
                    cursor += toCopy
                    toCopy
                }
                val reportedLen = if (totalLength > 0L) totalLength else C.LENGTH_UNSET.toLong()
                return DefaultExtractorInput(reader, position, reportedLen)
            }

            val factory = DefaultExtractorsFactory()
                .setSubtitleParserFactory(SubtitleParser.Factory.UNSUPPORTED)
                .setTextTrackTranscodingEnabled(false)

            var matchedExtractor: Extractor? = null
            for (candidate in factory.createExtractors()) {
                val sniffInput = createRemoteInput(0L) ?: break
                val ok = try {
                    candidate.sniff(sniffInput)
                } catch (_: Throwable) {
                    false
                }
                if (ok) {
                    matchedExtractor = candidate
                    break
                }
                runCatching { candidate.release() }
            }

            val extractor = matchedExtractor ?: return emptyList()
            try {
                val probeOutput = ProbingExtractorOutput()
                extractor.init(probeOutput)
                var input = createRemoteInput(0L) ?: return emptyList()
                val holder = PositionHolder()
                var steps = 0
                var seeks = 0
                while (steps < 1800) {
                    steps++
                    if (probeOutput.isReady()) break
                    val res = try {
                        extractor.read(input, holder)
                    } catch (_: EOFException) {
                        break
                    }
                    if (res == Extractor.RESULT_SEEK) {
                        seeks++
                        if (seeks > 5) break
                        input = createRemoteInput(holder.position) ?: break
                        continue
                    }
                    if (res == Extractor.RESULT_END_OF_INPUT) break
                }
                collectProbedTracks(probeOutput)
            } finally {
                runCatching { extractor.release() }
            }
        }.getOrElse { emptyList() }
    }

    private fun collectProbedTracks(output: ProbingExtractorOutput): List<ProbedMediaTrack> {
        var audioIdx = 0
        val result = ArrayList<ProbedMediaTrack>()
        for ((id, holder) in output.tracks) {
            val fmt = holder.format ?: continue
            val mime = fmt.sampleMimeType.orEmpty()
            val isAudio = MimeTypes.isAudio(mime)
            val isVideo = MimeTypes.isVideo(mime)
            if (!isAudio && !isVideo) continue
            val aOrder = if (isAudio) audioIdx++ else -1
            result.add(
                ProbedMediaTrack(
                    extractorTrackId = id,
                    audioOrderIndex = aOrder,
                    isAudio = isAudio,
                    isVideo = isVideo,
                    sampleMimeType = mime,
                    codecs = fmt.codecs.orEmpty(),
                    language = fmt.language.orEmpty(),
                    label = fmt.label.orEmpty(),
                    channelCount = fmt.channelCount.coerceAtLeast(0),
                    sampleRate = fmt.sampleRate.coerceAtLeast(0),
                    bitrate = fmt.bitrate.coerceAtLeast(0),
                    width = fmt.width.coerceAtLeast(0),
                    height = fmt.height.coerceAtLeast(0),
                    initializationData = fmt.initializationData,
                    media3Format = fmt
                )
            )
        }
        return result
    }

    /**
     * Herhangi bir medya dosyasından (`.ts`, fMP4, `.mp4`, `.mkv`, `.webm`, `.flv`, `.avi`, `.aac`, `.mp3`,
     * `.ac3`, `.eac3`, `.dts`, `.opus`, `.ogg`, `.flac`, `.wav`) istenen ses parçasını ayıklar:
     * - Evrensel 1–2 kanallı `AAC-LC` (`.m4a`) ve `MP3` (`.mp3`) akışları kayıpsız doğrudan paketler.
     * - Cihazların desteklemediği tüm kodekleri (`E-AC-3` / Atmos, `AC-3`, `DTS` / `DTS-HD`, `TrueHD`,
     *   `Opus`, `Vorbis`, `FLAC`, `ALAC`, `PCM` / `WAV`, `MP2`, `WMA`, `AMR`, 5.1/7.1 surround vb.)
     *   otomatik olarak her cihazda açılan evrensel Stereo **AAC-LC (`.m4a`)** biçimine dönüştürür.
     */
    fun extractAudioToStandaloneFile(
        sourceFile: File,
        destDir: File,
        baseNameWithoutExt: String,
        requestedAudioIndex: Int = -1,
        requestedLang: String? = null,
        requestedLabel: String? = null,
        preferredLanguages: List<String> = listOf("tr", "ja", "en")
    ): AudioExtractionResult {
        if (!sourceFile.exists() || sourceFile.length() <= 0L) {
            throw IOException("Kaynak ses dosyası bulunamadı veya boş")
        }
        destDir.mkdirs()

        val head = ByteArray(minOf(8192L, sourceFile.length()).toInt())
        RandomAccessFile(sourceFile, "r").use { it.readFully(head) }
        val sniffed = StreamFormatSniffer.sniff(head, head.size, url = sourceFile.name)

        val tracks = probeTracks(sourceFile)
        val audioTracks = tracks.filter { it.isAudio }

        if (audioTracks.isEmpty()) {
            if (sniffed == StreamFormatSniffer.DetectedFormat.M4A) {
                val outFile = File(destDir, "$baseNameWithoutExt.m4a")
                sourceFile.copyTo(outFile, overwrite = true)
                return AudioExtractionResult(outFile, "m4a", "audio/mp4", 1L)
            }
            if (sniffed in setOf(
                    StreamFormatSniffer.DetectedFormat.ADTS_AAC,
                    StreamFormatSniffer.DetectedFormat.MP3,
                    StreamFormatSniffer.DetectedFormat.AC3,
                    StreamFormatSniffer.DetectedFormat.EAC3,
                    StreamFormatSniffer.DetectedFormat.OPUS_OGG,
                    StreamFormatSniffer.DetectedFormat.VORBIS_OGG,
                    StreamFormatSniffer.DetectedFormat.FLAC,
                    StreamFormatSniffer.DetectedFormat.WAV,
                    StreamFormatSniffer.DetectedFormat.DTS
                )
            ) {
                val ext = StreamFormatSniffer.preferredAudioExtension(sniffed)
                val outFile = File(destDir, "$baseNameWithoutExt.$ext")
                val strippedOffset = if (sniffed in setOf(
                        StreamFormatSniffer.DetectedFormat.ADTS_AAC,
                        StreamFormatSniffer.DetectedFormat.AC3,
                        StreamFormatSniffer.DetectedFormat.EAC3,
                        StreamFormatSniffer.DetectedFormat.DTS
                    )
                ) {
                    StreamFormatSniffer.skipLeadingId3Tags(head, head.size)
                } else 0
                copyFromOffset(sourceFile, outFile, strippedOffset.toLong())
                return AudioExtractionResult(
                    outputFile = outFile,
                    extension = ext,
                    mimeType = "audio/$ext",
                    sampleCount = 1L
                )
            }
            throw IOException("Kaynak akış içinde ses parçası bulunamadı")
        }

        val chosenTrack = pickAudioTrack(
            audioTracks = audioTracks,
            requestedIndex = requestedAudioIndex,
            requestedLang = requestedLang,
            requestedLabel = requestedLabel,
            preferredLanguages = preferredLanguages
        )

        // Zaten doğrudan 1-2 kanallı evrensel MP3 dosyasıysa kopyala
        if (sniffed == StreamFormatSniffer.DetectedFormat.MP3 &&
            audioTracks.size == 1 &&
            tracks.none { it.isVideo } &&
            UniversalAudioTranscoder.isUniversalStandaloneCodec(
                chosenTrack.sampleMimeType,
                chosenTrack.codecs,
                chosenTrack.channelCount
            )
        ) {
            val outFile = File(destDir, "$baseNameWithoutExt.mp3")
            copyFromOffset(sourceFile, outFile, 0L)
            return AudioExtractionResult(outFile, "mp3", MimeTypes.AUDIO_MPEG, 1L)
        }

        return extractChosenAudioTrack(sourceFile, destDir, baseNameWithoutExt, chosenTrack)
    }

    private fun pickAudioTrack(
        audioTracks: List<ProbedMediaTrack>,
        requestedIndex: Int,
        requestedLang: String?,
        requestedLabel: String?,
        preferredLanguages: List<String>
    ): ProbedMediaTrack {
        if (requestedIndex in audioTracks.indices) {
            return audioTracks[requestedIndex]
        }
        if (!requestedLabel.isNullOrBlank()) {
            val clean = requestedLabel.trim()
            audioTracks.firstOrNull {
                it.label.equals(clean, ignoreCase = true) ||
                    (it.label.length >= 3 && clean.contains(it.label, ignoreCase = true))
            }?.let { return it }
        }
        val normReq = ExternalAudioTrack.normalizeLangCode(requestedLang)
        if (normReq.isNotBlank()) {
            audioTracks.firstOrNull { it.normalizedLang == normReq }?.let { return it }
        }
        for (pref in preferredLanguages) {
            val normPref = ExternalAudioTrack.normalizeLangCode(pref)
            if (normPref.isBlank()) continue
            audioTracks.firstOrNull { it.normalizedLang == normPref }?.let { return it }
        }
        return audioTracks.first()
    }

    private fun extractChosenAudioTrack(
        sourceFile: File,
        destDir: File,
        baseNameWithoutExt: String,
        target: ProbedMediaTrack
    ): AudioExtractionResult {
        val mime = target.sampleMimeType.lowercase(Locale.ROOT)
        val isUniversal = UniversalAudioTranscoder.isUniversalStandaloneCodec(
            sampleMimeType = mime,
            codecs = target.codecs,
            channelCount = target.channelCount.takeIf { it > 0 } ?: 2
        )

        if (!isUniversal) {
            val universalM4a = File(destDir, "$baseNameWithoutExt.m4a")
            val transcoded = runCatching {
                UniversalAudioTranscoder.transcodeTrackToUniversalM4a(
                    sourceFile = sourceFile,
                    targetTrack = target,
                    outM4aFile = universalM4a
                )
            }.onFailure { e ->
                Log.w(TAG, "Evrensel M4A ses dönüştürme geri dönüşü (${target.sampleMimeType}): ${e.message}")
            }.getOrDefault(false)

            if (transcoded && universalM4a.exists() && universalM4a.length() > 256L) {
                return AudioExtractionResult(
                    outputFile = universalM4a,
                    extension = "m4a",
                    mimeType = "audio/mp4",
                    sampleCount = 1L
                )
            }
            universalM4a.delete()
        }

        return when {
            mime == MimeTypes.AUDIO_AAC -> extractAacStandalone(sourceFile, destDir, baseNameWithoutExt, target)
            mime == MimeTypes.AUDIO_MPEG -> extractRawElementaryAudio(sourceFile, destDir, baseNameWithoutExt, target, "mp3")
            mime == MimeTypes.AUDIO_OPUS -> extractOpusStandalone(sourceFile, destDir, baseNameWithoutExt, target)
            mime == MimeTypes.AUDIO_VORBIS -> extractVorbisStandalone(sourceFile, destDir, baseNameWithoutExt, target)
            mime == MimeTypes.AUDIO_FLAC -> extractFlacStandalone(sourceFile, destDir, baseNameWithoutExt, target)
            mime == MimeTypes.AUDIO_RAW -> extractWavStandalone(sourceFile, destDir, baseNameWithoutExt, target)
            mime == MimeTypes.AUDIO_MPEG_L1 || mime == MimeTypes.AUDIO_MPEG_L2 ->
                extractRawElementaryAudio(sourceFile, destDir, baseNameWithoutExt, target, "mp3")
            mime == MimeTypes.AUDIO_E_AC3 || mime == MimeTypes.AUDIO_E_AC3_JOC ->
                extractRawElementaryAudio(sourceFile, destDir, baseNameWithoutExt, target, "eac3")
            mime == MimeTypes.AUDIO_AC3 ->
                extractRawElementaryAudio(sourceFile, destDir, baseNameWithoutExt, target, "ac3")
            mime == MimeTypes.AUDIO_DTS || mime == MimeTypes.AUDIO_DTS_HD || mime == MimeTypes.AUDIO_DTS_EXPRESS ->
                extractRawElementaryAudio(sourceFile, destDir, baseNameWithoutExt, target, "dts")
            else -> {
                tryExtractViaMediaMuxer(
                    sourceFile = sourceFile,
                    outFile = File(destDir, "$baseNameWithoutExt.m4a"),
                    outputFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
                    target = target,
                    extension = "m4a"
                ) ?: extractRawElementaryAudio(sourceFile, destDir, baseNameWithoutExt, target, "m4a")
            }
        }
    }

    private fun extractAacStandalone(
        sourceFile: File,
        destDir: File,
        baseNameWithoutExt: String,
        target: ProbedMediaTrack
    ): AudioExtractionResult {
        val m4aFile = File(destDir, "$baseNameWithoutExt.m4a")
        val muxed = tryExtractViaMediaMuxer(
            sourceFile = sourceFile,
            outFile = m4aFile,
            outputFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
            target = target,
            extension = "m4a"
        )
        if (muxed != null && muxed.sampleCount > 0L && m4aFile.length() > 128L) {
            return muxed
        }
        m4aFile.delete()

        // Android MediaMuxer mevcut değilse (ör. JVM birim testi veya özel AAC profili) RFC uyumlu ADTS (.aac) üret
        val aacFile = File(destDir, "$baseNameWithoutExt.aac")
        var count = 0L
        BufferedOutputStream(FileOutputStream(aacFile)).use { out ->
            streamSingleTrackSamples(sourceFile, target.extractorTrackId) { format, sampleBytes, _, _ ->
                val activeFmt = format ?: return@streamSingleTrackSamples
                val frame = ensureAdtsFrame(sampleBytes, activeFmt)
                out.write(frame)
                count++
            }
            out.flush()
        }
        if (count == 0L || aacFile.length() <= 0L) {
            aacFile.delete()
            throw IOException("AAC ses kareleri ayıklanamadı")
        }
        return AudioExtractionResult(aacFile, "aac", MimeTypes.AUDIO_AAC, count)
    }

    private fun extractOpusStandalone(
        sourceFile: File,
        destDir: File,
        baseNameWithoutExt: String,
        target: ProbedMediaTrack
    ): AudioExtractionResult {
        val opusFile = File(destDir, "$baseNameWithoutExt.opus")
        var count = 0L
        BufferedOutputStream(FileOutputStream(opusFile)).use { rawOut ->
            val oggWriter = OggOpusStreamWriter(
                out = rawOut,
                channels = target.channelCount.takeIf { it > 0 } ?: 2,
                sampleRate = target.sampleRate.takeIf { it > 0 } ?: 48_000,
                opusHead = target.initializationData.firstOrNull()
            )
            oggWriter.writeHeaders()
            streamSingleTrackSamples(sourceFile, target.extractorTrackId) { _, sampleBytes, timeUs, _ ->
                if (sampleBytes.isNotEmpty()) {
                    oggWriter.writeAudioPacket(sampleBytes, timeUs)
                    count++
                }
            }
            oggWriter.finish()
            rawOut.flush()
        }
        if (count == 0L || opusFile.length() <= 0L) {
            opusFile.delete()
            throw IOException("Opus ses paketleri ayıklanamadı")
        }
        return AudioExtractionResult(opusFile, "opus", MimeTypes.AUDIO_OPUS, count)
    }

    private fun extractVorbisStandalone(
        sourceFile: File,
        destDir: File,
        baseNameWithoutExt: String,
        target: ProbedMediaTrack
    ): AudioExtractionResult {
        val webmFile = File(destDir, "$baseNameWithoutExt.webm")
        val muxed = tryExtractViaMediaMuxer(
            sourceFile = sourceFile,
            outFile = webmFile,
            outputFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM,
            target = target,
            extension = "webm"
        )
        if (muxed != null && muxed.sampleCount > 0L) return muxed
        webmFile.delete()
        return extractRawElementaryAudio(sourceFile, destDir, baseNameWithoutExt, target, "ogg")
    }

    private fun extractFlacStandalone(
        sourceFile: File,
        destDir: File,
        baseNameWithoutExt: String,
        target: ProbedMediaTrack
    ): AudioExtractionResult {
        val flacFile = File(destDir, "$baseNameWithoutExt.flac")
        var count = 0L
        BufferedOutputStream(FileOutputStream(flacFile)).use { out ->
            var headerWritten = false
            streamSingleTrackSamples(sourceFile, target.extractorTrackId) { fmt, sampleBytes, _, _ ->
                if (!headerWritten) {
                    headerWritten = true
                    val initBlock = fmt?.initializationData?.firstOrNull()
                        ?: target.initializationData.firstOrNull()
                    if (initBlock != null && initBlock.isNotEmpty()) {
                        if (initBlock.size >= 4 &&
                            initBlock[0] == 'f'.code.toByte() &&
                            initBlock[1] == 'L'.code.toByte() &&
                            initBlock[2] == 'a'.code.toByte() &&
                            initBlock[3] == 'C'.code.toByte()
                        ) {
                            out.write(initBlock)
                        } else {
                            out.write(byteArrayOf('f'.code.toByte(), 'L'.code.toByte(), 'a'.code.toByte(), 'C'.code.toByte()))
                            // Son metadata bloğu bitini (0x80) işaretle (34 baytlık ham STREAMINFO ise)
                            if (initBlock.size == 34) {
                                out.write(byteArrayOf(0x80.toByte(), 0x00, 0x00, 34))
                            }
                            out.write(initBlock)
                        }
                    }
                }
                if (sampleBytes.isNotEmpty()) {
                    out.write(sampleBytes)
                    count++
                }
            }
            out.flush()
        }
        if (count == 0L || flacFile.length() <= 0L) {
            flacFile.delete()
            throw IOException("FLAC ses kareleri ayıklanamadı")
        }
        return AudioExtractionResult(flacFile, "flac", MimeTypes.AUDIO_FLAC, count)
    }

    private fun extractWavStandalone(
        sourceFile: File,
        destDir: File,
        baseNameWithoutExt: String,
        target: ProbedMediaTrack
    ): AudioExtractionResult {
        val wavFile = File(destDir, "$baseNameWithoutExt.wav")
        val channels = (target.channelCount.takeIf { it > 0 } ?: 2).coerceIn(1, 8)
        val sampleRate = (target.sampleRate.takeIf { it > 0 } ?: 44_100)
        val bitsPerSample = 16
        var pcmDataBytes = 0L
        var count = 0L

        RandomAccessFile(wavFile, "rw").use { raf ->
            raf.setLength(0L)
            raf.write(ByteArray(44)) // Başlık yer tutucusu
            streamSingleTrackSamples(sourceFile, target.extractorTrackId) { _, sampleBytes, _, _ ->
                if (sampleBytes.isNotEmpty()) {
                    raf.write(sampleBytes)
                    pcmDataBytes += sampleBytes.size
                    count++
                }
            }
            raf.seek(0L)
            raf.write(buildWavHeader(pcmDataBytes, sampleRate, channels, bitsPerSample))
        }
        if (count == 0L || pcmDataBytes <= 0L) {
            wavFile.delete()
            throw IOException("WAV PCM verisi ayıklanamadı")
        }
        return AudioExtractionResult(wavFile, "wav", MimeTypes.AUDIO_WAV, count)
    }

    private fun extractRawElementaryAudio(
        sourceFile: File,
        destDir: File,
        baseNameWithoutExt: String,
        target: ProbedMediaTrack,
        extension: String
    ): AudioExtractionResult {
        val outFile = File(destDir, "$baseNameWithoutExt.$extension")
        var count = 0L
        BufferedOutputStream(FileOutputStream(outFile)).use { out ->
            streamSingleTrackSamples(sourceFile, target.extractorTrackId) { _, sampleBytes, _, _ ->
                if (sampleBytes.isNotEmpty()) {
                    out.write(sampleBytes)
                    count++
                }
            }
            out.flush()
        }
        if (count == 0L || outFile.length() <= 0L) {
            outFile.delete()
            throw IOException("Ses örnekleri ($extension) ayıklanamadı")
        }
        return AudioExtractionResult(outFile, extension, target.sampleMimeType, count)
    }

    private fun tryExtractViaMediaMuxer(
        sourceFile: File,
        outFile: File,
        outputFormat: Int,
        target: ProbedMediaTrack,
        extension: String
    ): AudioExtractionResult? {
        return runCatching {
            outFile.parentFile?.mkdirs()
            if (outFile.exists()) outFile.delete()
            var muxer: MediaMuxer? = null
            var muxerTrackIdx = -1
            var started = false
            var sampleCount = 0L
            var lastTimeUs = -1L
            val defaultFrameDurUs = if (target.sampleRate > 0) (1_024_000_000L / target.sampleRate) else 21_333L

            try {
                streamSingleTrackSamples(sourceFile, target.extractorTrackId) { fmt, sampleBytes, timeUs, flags ->
                    if (sampleBytes.isEmpty()) return@streamSingleTrackSamples
                    if (!started) {
                        val activeFmt = fmt ?: return@streamSingleTrackSamples
                        val mediaFormat = toAndroidAudioMediaFormat(activeFmt, target)
                        val m = MediaMuxer(outFile.absolutePath, outputFormat)
                        muxerTrackIdx = m.addTrack(mediaFormat)
                        m.start()
                        muxer = m
                        started = true
                    }
                    val safeTimeUs = when {
                        timeUs < 0L -> if (lastTimeUs < 0L) 0L else lastTimeUs + defaultFrameDurUs
                        lastTimeUs >= 0L && timeUs <= lastTimeUs -> lastTimeUs + 1L
                        else -> timeUs
                    }
                    lastTimeUs = safeTimeUs
                    val info = MediaCodec.BufferInfo().apply {
                        set(
                            0,
                            sampleBytes.size,
                            safeTimeUs,
                            if ((flags and C.BUFFER_FLAG_KEY_FRAME) != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                        )
                    }
                    muxer!!.writeSampleData(muxerTrackIdx, ByteBuffer.wrap(sampleBytes), info)
                    sampleCount++
                }
                if (started && sampleCount > 0L) {
                    muxer?.stop()
                }
            } finally {
                runCatching { muxer?.release() }
            }

            if (sampleCount > 0L && outFile.exists() && outFile.length() > 0L) {
                AudioExtractionResult(outFile, extension, target.sampleMimeType, sampleCount)
            } else {
                outFile.delete()
                null
            }
        }.onFailure { e ->
            Log.w(TAG, "MediaMuxer ses çıkarma geri dönüşü (${e.javaClass.simpleName}): ${e.message}")
            outFile.delete()
        }.getOrNull()
    }

    /**
     * Video + ses akışlarını kayıpsız birleştirir veya yeniden paketler (remux):
     * - Görüntü + birincil ses MP4 veya WebM içinde birleşebiliyorsa tek dosyada birleştirir.
     * - Birincil ses kodeki MP4 tarafından reddedilirse (ör. E-AC-3 / AC-3 / DTS / FLAC) veya
     *   ek ses dilleri (`additionalAudios` - ör. Türkçe Dublaj + Orijinal Ses) varsa, hiçbir sesi
     *   kaybetmeden yan (companion) ses dosyası olarak üretir.
     */
    fun remuxVideoWithAudios(
        rawVideoFile: File,
        externalAudioInputs: List<Pair<ExternalAudioTrack, File>>,
        workDir: File,
        preferredLanguages: List<String> = listOf("tr", "ja", "en")
    ): VideoRemuxResult {
        val videoTracks = probeTracks(rawVideoFile)
        val embeddedVideos = videoTracks.filter { it.isVideo }
        val embeddedAudios = videoTracks.filter { it.isAudio }

        // 1) Ayrık ses dosyası YOKSA (gömülü HLS .ts veya fMP4 akışı)
        if (externalAudioInputs.isEmpty()) {
            return remuxSingleStreamVideo(rawVideoFile, embeddedVideos, embeddedAudios, workDir, preferredLanguages)
        }

        // 2) Ayrık ses dosyaları VARSA (Demuxed HLS, DASH .mpd, ExtractorLink.audioTracks, YouTube)
        val vTrack = embeddedVideos.firstOrNull()
        val orderedExternal = orderExternalAudiosByPreference(externalAudioInputs, preferredLanguages)
        val primaryPair = orderedExternal.first()
        val secondaryPairs = orderedExternal.drop(1)

        val primaryAudioTracks = probeTracks(primaryPair.second).filter { it.isAudio }
        val pAudio = primaryAudioTracks.firstOrNull()

        if (vTrack != null && pAudio != null) {
            val useWebm = isWebmCompatible(vTrack.sampleMimeType, pAudio.sampleMimeType)
            val isDirectMp4Audio = UniversalAudioTranscoder.isUniversalMp4VideoMuxCodec(
                pAudio.sampleMimeType,
                pAudio.codecs,
                pAudio.channelCount.takeIf { it > 0 } ?: 2
            )
            val useDirectMp4 = !useWebm && isMp4VideoCompatible(vTrack.sampleMimeType) && isDirectMp4Audio
            if (useWebm || useDirectMp4) {
                val outExt = if (useWebm) "webm" else "mp4"
                val outFormat = if (useWebm) {
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
                } else {
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
                }
                val mergedOut = File(workDir, "merged_output.$outExt")
                val muxSuccess = tryMuxSeparateVideoAndAudio(
                    videoFile = rawVideoFile,
                    videoTrack = vTrack,
                    audioFile = primaryPair.second,
                    audioTrack = pAudio,
                    outFile = mergedOut,
                    outputFormat = outFormat
                )
                if (muxSuccess && mergedOut.exists() && mergedOut.length() > 1024L) {
                    val companions = exportSecondaryCompanions(secondaryPairs, workDir)
                    return VideoRemuxResult(
                        outputFile = mergedOut,
                        extension = outExt,
                        audioMergedIntoContainer = true,
                        companionAudioFiles = companions
                    )
                }
                mergedOut.delete()
            }

            // Birincil ses MP4 ile uyumsuzsa (E-AC-3, AC-3, DTS, FLAC, Opus, 5.1 surround vb.)
            // otomatik olarak evrensel Stereo AAC-LC (.m4a) biçimine dönüştürüp MP4 içine göm!
            if (!useWebm && isMp4VideoCompatible(vTrack.sampleMimeType)) {
                val transcodedAudio = File(workDir, "transcoded_primary_aac.m4a")
                val transcodedOk = runCatching {
                    UniversalAudioTranscoder.transcodeTrackToUniversalM4a(
                        sourceFile = primaryPair.second,
                        targetTrack = pAudio,
                        outM4aFile = transcodedAudio
                    )
                }.getOrDefault(false)
                if (transcodedOk && transcodedAudio.exists() && transcodedAudio.length() > 256L) {
                    val aacProbed = probeTracks(transcodedAudio).firstOrNull { it.isAudio }
                    if (aacProbed != null) {
                        val mergedOut = File(workDir, "merged_output.mp4")
                        val muxSuccess = tryMuxSeparateVideoAndAudio(
                            videoFile = rawVideoFile,
                            videoTrack = vTrack,
                            audioFile = transcodedAudio,
                            audioTrack = aacProbed,
                            outFile = mergedOut,
                            outputFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
                        )
                        transcodedAudio.delete()
                        if (muxSuccess && mergedOut.exists() && mergedOut.length() > 1024L) {
                            val companions = exportSecondaryCompanions(secondaryPairs, workDir)
                            return VideoRemuxResult(
                                outputFile = mergedOut,
                                extension = "mp4",
                                audioMergedIntoContainer = true,
                                companionAudioFiles = companions
                            )
                        }
                        mergedOut.delete()
                    } else {
                        transcodedAudio.delete()
                    }
                } else {
                    transcodedAudio.delete()
                }
            }
        }

        // MediaMuxer birleştiremediyse yedek güvenlik ağı: videoyu hazırla ve tüm sesleri yan dosya olarak üret
        val cleanVideoResult = remuxSingleStreamVideo(rawVideoFile, embeddedVideos, emptyList(), workDir, preferredLanguages)
        val allCompanions = exportSecondaryCompanions(orderedExternal, workDir)
        return VideoRemuxResult(
            outputFile = cleanVideoResult.outputFile,
            extension = cleanVideoResult.extension,
            audioMergedIntoContainer = false,
            companionAudioFiles = allCompanions
        )
    }

    private fun remuxSingleStreamVideo(
        rawVideoFile: File,
        embeddedVideos: List<ProbedMediaTrack>,
        embeddedAudios: List<ProbedMediaTrack>,
        workDir: File,
        preferredLanguages: List<String>
    ): VideoRemuxResult {
        val head = ByteArray(minOf(4096L, rawVideoFile.length()).toInt())
        RandomAccessFile(rawVideoFile, "r").use { it.readFully(head) }
        val sniffed = StreamFormatSniffer.sniff(head, head.size, url = rawVideoFile.name)

        val vTrack = embeddedVideos.firstOrNull()
        val primaryEmbeddedAudio = if (embeddedAudios.isNotEmpty()) {
            pickAudioTrack(embeddedAudios, -1, null, null, preferredLanguages)
        } else null

        if (vTrack != null &&
            isMp4VideoCompatible(vTrack.sampleMimeType) &&
            sniffed in setOf(
                StreamFormatSniffer.DetectedFormat.MPEG_TS,
                StreamFormatSniffer.DetectedFormat.FMP4_SEGMENT,
                StreamFormatSniffer.DetectedFormat.MP4,
                StreamFormatSniffer.DetectedFormat.FLV,
                StreamFormatSniffer.DetectedFormat.AVI
            )
        ) {
            val audioAlreadyUniversalMp4 = primaryEmbeddedAudio == null ||
                UniversalAudioTranscoder.isUniversalMp4VideoMuxCodec(
                    primaryEmbeddedAudio.sampleMimeType,
                    primaryEmbeddedAudio.codecs,
                    primaryEmbeddedAudio.channelCount.takeIf { it > 0 } ?: 2
                )

            val needsContainerRemux = sniffed != StreamFormatSniffer.DetectedFormat.MP4 || hasMoofFragment(head)

            if (audioAlreadyUniversalMp4 && needsContainerRemux) {
                val remuxedMp4 = File(workDir, "remuxed_single.mp4")
                val ok = tryRemuxMuxedFileToMp4(rawVideoFile, vTrack, primaryEmbeddedAudio, remuxedMp4)
                if (ok && remuxedMp4.exists() && remuxedMp4.length() > 1024L) {
                    val extraAudios = embeddedAudios.filter { it.extractorTrackId != primaryEmbeddedAudio?.extractorTrackId }
                    val extraCompanions = extraAudios.mapIndexedNotNull { idx, extra ->
                        runCatching {
                            val lang = extra.normalizedLang.ifBlank { "track${idx + 2}" }
                            val extracted = extractChosenAudioTrack(rawVideoFile, workDir, "companion_emb_$idx", extra)
                            CompanionAudioResult(
                                file = extracted.outputFile,
                                lang = lang,
                                label = extra.label,
                                extension = extracted.extension
                            )
                        }.getOrNull()
                    }
                    return VideoRemuxResult(
                        outputFile = remuxedMp4,
                        extension = "mp4",
                        audioMergedIntoContainer = primaryEmbeddedAudio != null,
                        companionAudioFiles = extraCompanions
                    )
                }
                remuxedMp4.delete()
            } else if (!audioAlreadyUniversalMp4 && primaryEmbeddedAudio != null) {
                // Gömülü E-AC-3 / AC-3 / DTS / MP2 / FLAC / 5.1 sesi evrensel AAC-LC'ye çevirip MP4 içine göm
                val transcodedEmb = File(workDir, "transcoded_emb_aac.m4a")
                val transcodedOk = runCatching {
                    UniversalAudioTranscoder.transcodeTrackToUniversalM4a(
                        sourceFile = rawVideoFile,
                        targetTrack = primaryEmbeddedAudio,
                        outM4aFile = transcodedEmb
                    )
                }.getOrDefault(false)
                if (transcodedOk && transcodedEmb.exists() && transcodedEmb.length() > 256L) {
                    val aacTrack = probeTracks(transcodedEmb).firstOrNull { it.isAudio }
                    if (aacTrack != null) {
                        val remuxedMp4 = File(workDir, "remuxed_single.mp4")
                        val muxOk = tryMuxSeparateVideoAndAudio(
                            videoFile = rawVideoFile,
                            videoTrack = vTrack,
                            audioFile = transcodedEmb,
                            audioTrack = aacTrack,
                            outFile = remuxedMp4,
                            outputFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
                        )
                        transcodedEmb.delete()
                        if (muxOk && remuxedMp4.exists() && remuxedMp4.length() > 1024L) {
                            val extraAudios = embeddedAudios.filter { it.extractorTrackId != primaryEmbeddedAudio.extractorTrackId }
                            val extraCompanions = extraAudios.mapIndexedNotNull { idx, extra ->
                                runCatching {
                                    val lang = extra.normalizedLang.ifBlank { "track${idx + 2}" }
                                    val extracted = extractChosenAudioTrack(rawVideoFile, workDir, "companion_emb_$idx", extra)
                                    CompanionAudioResult(
                                        file = extracted.outputFile,
                                        lang = lang,
                                        label = extra.label,
                                        extension = extracted.extension
                                    )
                                }.getOrNull()
                            }
                            return VideoRemuxResult(
                                outputFile = remuxedMp4,
                                extension = "mp4",
                                audioMergedIntoContainer = true,
                                companionAudioFiles = extraCompanions
                            )
                        }
                        remuxedMp4.delete()
                    } else {
                        transcodedEmb.delete()
                    }
                } else {
                    transcodedEmb.delete()
                }
            }
        }

        val finalExt = StreamFormatSniffer.preferredVideoExtension(sniffed)
        return VideoRemuxResult(
            outputFile = rawVideoFile,
            extension = finalExt,
            audioMergedIntoContainer = embeddedAudios.isNotEmpty()
        )
    }

    private fun tryRemuxMuxedFileToMp4(
        sourceFile: File,
        videoTrack: ProbedMediaTrack,
        audioTrack: ProbedMediaTrack?,
        outFile: File
    ): Boolean {
        return runCatching {
            if (outFile.exists()) outFile.delete()
            val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var videoMuxIdx = -1
            var audioMuxIdx = -1
            var videoSamples = 0L
            var audioSamples = 0L
            var lastVideoUs = -1L
            var lastAudioUs = -1L

            try {
                videoMuxIdx = muxer.addTrack(toAndroidVideoMediaFormat(videoTrack))
                if (audioTrack != null) {
                    audioMuxIdx = muxer.addTrack(toAndroidAudioMediaFormat(null, audioTrack))
                }
                muxer.start()

                val targetIds = buildSet {
                    add(videoTrack.extractorTrackId)
                    if (audioTrack != null) add(audioTrack.extractorTrackId)
                }

                streamMultipleTracksSamples(sourceFile, targetIds) { trackId, _, bytes, timeUs, flags ->
                    if (bytes.isEmpty()) return@streamMultipleTracksSamples
                    if (trackId == videoTrack.extractorTrackId) {
                        val safeUs = when {
                            timeUs < 0L -> if (lastVideoUs < 0L) 0L else lastVideoUs + 33_333L
                            lastVideoUs >= 0L && timeUs <= lastVideoUs -> lastVideoUs + 1L
                            else -> timeUs
                        }
                        lastVideoUs = safeUs
                        val info = MediaCodec.BufferInfo().apply {
                            set(
                                0,
                                bytes.size,
                                safeUs,
                                if ((flags and C.BUFFER_FLAG_KEY_FRAME) != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                            )
                        }
                        muxer.writeSampleData(videoMuxIdx, ByteBuffer.wrap(bytes), info)
                        videoSamples++
                    } else if (audioTrack != null && trackId == audioTrack.extractorTrackId && audioMuxIdx >= 0) {
                        val safeUs = when {
                            timeUs < 0L -> if (lastAudioUs < 0L) 0L else lastAudioUs + 21_333L
                            lastAudioUs >= 0L && timeUs <= lastAudioUs -> lastAudioUs + 1L
                            else -> timeUs
                        }
                        lastAudioUs = safeUs
                        val info = MediaCodec.BufferInfo().apply {
                            set(0, bytes.size, safeUs, MediaCodec.BUFFER_FLAG_KEY_FRAME)
                        }
                        muxer.writeSampleData(audioMuxIdx, ByteBuffer.wrap(bytes), info)
                        audioSamples++
                    }
                }

                val valid = videoSamples > 0L && (audioTrack == null || audioSamples > 0L)
                if (valid) {
                    muxer.stop()
                }
                valid
            } finally {
                runCatching { muxer.release() }
            }
        }.onFailure { e ->
            Log.w(TAG, "tryRemuxMuxedFileToMp4 başarısız: ${e.message}")
            outFile.delete()
        }.getOrDefault(false)
    }

    private fun tryMuxSeparateVideoAndAudio(
        videoFile: File,
        videoTrack: ProbedMediaTrack,
        audioFile: File,
        audioTrack: ProbedMediaTrack,
        outFile: File,
        outputFormat: Int
    ): Boolean {
        return runCatching {
            if (outFile.exists()) outFile.delete()
            val muxer = MediaMuxer(outFile.absolutePath, outputFormat)
            var videoSamples = 0L
            var audioSamples = 0L
            try {
                val vIdx = muxer.addTrack(toAndroidVideoMediaFormat(videoTrack))
                val aIdx = muxer.addTrack(toAndroidAudioMediaFormat(null, audioTrack))
                muxer.start()

                var firstVideoPtsUs: Long? = null
                var lastVideoUs = -1L
                streamSingleTrackSamples(videoFile, videoTrack.extractorTrackId) { _, bytes, timeUs, flags ->
                    if (bytes.isEmpty()) return@streamSingleTrackSamples
                    val rawPts = if (timeUs < 0L) 0L else timeUs
                    if (firstVideoPtsUs == null) firstVideoPtsUs = rawPts
                    val normalizedPts = (rawPts - (firstVideoPtsUs ?: 0L)).coerceAtLeast(0L)
                    val safeUs = if (lastVideoUs >= 0L && normalizedPts <= lastVideoUs) lastVideoUs + 1L else normalizedPts
                    lastVideoUs = safeUs
                    val info = MediaCodec.BufferInfo().apply {
                        set(
                            0,
                            bytes.size,
                            safeUs,
                            if ((flags and C.BUFFER_FLAG_KEY_FRAME) != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                        )
                    }
                    muxer.writeSampleData(vIdx, ByteBuffer.wrap(bytes), info)
                    videoSamples++
                }

                var firstAudioPtsUs: Long? = null
                var lastAudioUs = -1L
                streamSingleTrackSamples(audioFile, audioTrack.extractorTrackId) { _, bytes, timeUs, _ ->
                    if (bytes.isEmpty()) return@streamSingleTrackSamples
                    val rawPts = if (timeUs < 0L) 0L else timeUs
                    if (firstAudioPtsUs == null) firstAudioPtsUs = rawPts
                    val normalizedPts = (rawPts - (firstAudioPtsUs ?: 0L)).coerceAtLeast(0L)
                    val safeUs = if (lastAudioUs >= 0L && normalizedPts <= lastAudioUs) lastAudioUs + 1L else normalizedPts
                    lastAudioUs = safeUs
                    val info = MediaCodec.BufferInfo().apply {
                        set(0, bytes.size, safeUs, MediaCodec.BUFFER_FLAG_KEY_FRAME)
                    }
                    muxer.writeSampleData(aIdx, ByteBuffer.wrap(bytes), info)
                    audioSamples++
                }

                val ok = videoSamples > 0L && audioSamples > 0L
                if (ok) {
                    muxer.stop()
                }
                ok
            } finally {
                runCatching { muxer.release() }
            }
        }.onFailure { e ->
            Log.w(TAG, "tryMuxSeparateVideoAndAudio başarısız: ${e.message}")
            outFile.delete()
        }.getOrDefault(false)
    }

    private fun exportSecondaryCompanions(
        pairs: List<Pair<ExternalAudioTrack, File>>,
        workDir: File
    ): List<CompanionAudioResult> {
        return pairs.mapIndexedNotNull { index, (meta, rawFile) ->
            runCatching {
                val extracted = extractAudioToStandaloneFile(
                    sourceFile = rawFile,
                    destDir = workDir,
                    baseNameWithoutExt = "companion_ext_$index"
                )
                val lang = ExternalAudioTrack.normalizeLangCode(
                    meta.safeLang.ifBlank { ExternalAudioTrack.inferLanguageFromText("${meta.safeLabel} ${meta.url}") }
                ).ifBlank { "track${index + 1}" }
                CompanionAudioResult(
                    file = extracted.outputFile,
                    lang = lang,
                    label = meta.safeLabel,
                    extension = extracted.extension
                )
            }.onFailure { e ->
                Log.w(TAG, "Yan ses dosyası oluşturulamadı (${meta.safeLabel}): ${e.message}")
            }.getOrNull()
        }
    }

    private fun orderExternalAudiosByPreference(
        inputs: List<Pair<ExternalAudioTrack, File>>,
        preferredLanguages: List<String>
    ): List<Pair<ExternalAudioTrack, File>> {
        if (inputs.size <= 1) return inputs
        return inputs.sortedWith(
            compareBy<Pair<ExternalAudioTrack, File>> { (track, _) ->
                val norm = ExternalAudioTrack.normalizeLangCode(
                    track.safeLang.ifBlank { ExternalAudioTrack.inferLanguageFromText("${track.safeLabel} ${track.url}") }
                )
                val idx = preferredLanguages
                    .map { ExternalAudioTrack.normalizeLangCode(it) }
                    .indexOf(norm)
                if (idx >= 0) idx else Int.MAX_VALUE
            }.thenByDescending { (track, _) -> track.isDefault }
        )
    }

    private fun isWebmCompatible(videoMime: String, audioMime: String): Boolean {
        val v = videoMime.lowercase(Locale.ROOT)
        val a = audioMime.lowercase(Locale.ROOT)
        val vOk = v == MimeTypes.VIDEO_VP8 || v == MimeTypes.VIDEO_VP9
        val aOk = a == MimeTypes.AUDIO_OPUS || a == MimeTypes.AUDIO_VORBIS
        return vOk && aOk
    }

    private fun isMp4MuxCompatible(videoMime: String, audioMime: String): Boolean =
        isMp4VideoCompatible(videoMime) && isMp4AudioCompatible(audioMime)

    private fun isMp4VideoCompatible(videoMime: String): Boolean {
        val v = videoMime.lowercase(Locale.ROOT)
        return v == MimeTypes.VIDEO_H264 ||
            v == MimeTypes.VIDEO_H265 ||
            v == MimeTypes.VIDEO_MP4V ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && v == MimeTypes.VIDEO_AV1)
    }

    private fun isMp4AudioCompatible(audioMime: String): Boolean {
        val a = audioMime.lowercase(Locale.ROOT)
        return a == MimeTypes.AUDIO_AAC ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && a == MimeTypes.AUDIO_OPUS)
    }

    private fun hasMoofFragment(head: ByteArray): Boolean {
        val ascii = String(head, Charsets.ISO_8859_1)
        return ascii.contains("moof") || ascii.contains("sidx")
    }

    private fun toAndroidVideoMediaFormat(track: ProbedMediaTrack): MediaFormat {
        val width = track.width.takeIf { it > 0 } ?: 1920
        val height = track.height.takeIf { it > 0 } ?: 1080
        val mf = MediaFormat.createVideoFormat(track.sampleMimeType, width, height)
        track.initializationData.forEachIndexed { idx, bytes ->
            mf.setByteBuffer("csd-$idx", ByteBuffer.wrap(bytes))
        }
        if (track.bitrate > 0) {
            mf.setInteger(MediaFormat.KEY_BIT_RATE, track.bitrate)
        }
        return mf
    }

    private fun toAndroidAudioMediaFormat(liveFormat: Format?, probed: ProbedMediaTrack): MediaFormat {
        val mime = liveFormat?.sampleMimeType ?: probed.sampleMimeType
        val sampleRate = (liveFormat?.sampleRate?.takeIf { it > 0 } ?: probed.sampleRate.takeIf { it > 0 } ?: 44_100)
        val channels = (liveFormat?.channelCount?.takeIf { it > 0 } ?: probed.channelCount.takeIf { it > 0 } ?: 2)
        val mf = MediaFormat.createAudioFormat(mime, sampleRate, channels)
        val initList = liveFormat?.initializationData?.takeIf { it.isNotEmpty() } ?: probed.initializationData
        if (initList.isNotEmpty()) {
            initList.forEachIndexed { idx, bytes ->
                mf.setByteBuffer("csd-$idx", ByteBuffer.wrap(bytes))
            }
        } else if (mime.equals(MimeTypes.AUDIO_AAC, ignoreCase = true)) {
            mf.setByteBuffer("csd-0", ByteBuffer.wrap(buildAacAudioSpecificConfig(sampleRate, channels)))
        }
        val lang = (liveFormat?.language ?: probed.language).trim()
        if (lang.isNotBlank()) {
            mf.setString(MediaFormat.KEY_LANGUAGE, lang)
        }
        val br = liveFormat?.bitrate?.takeIf { it > 0 } ?: probed.bitrate.takeIf { it > 0 } ?: 128_000
        mf.setInteger(MediaFormat.KEY_BIT_RATE, br)
        return mf
    }

    // ── ADTS AAC Paketleyici (ISO/IEC 14496-3) ──────────────────────────────

    private val AAC_SAMPLE_RATES = intArrayOf(
        96000, 88200, 64000, 48000, 44100, 32000, 24000,
        22050, 16000, 12000, 11025, 8000, 7350
    )

    internal fun buildAacAudioSpecificConfig(sampleRate: Int, channelCount: Int): ByteArray {
        val freqIdx = AAC_SAMPLE_RATES.indexOfFirst { it == sampleRate }.takeIf { it >= 0 } ?: 4 // 44100 Hz
        val aot = 2 // AAC-LC
        val chanCfg = channelCount.coerceIn(1, 7)
        val b0 = ((aot shl 3) or (freqIdx ushr 1)) and 0xFF
        val b1 = (((freqIdx and 0x01) shl 7) or (chanCfg shl 3)) and 0xFF
        return byteArrayOf(b0.toByte(), b1.toByte())
    }

    internal fun ensureAdtsFrame(rawSample: ByteArray, format: Format): ByteArray {
        if (rawSample.size >= 7 &&
            (rawSample[0].toInt() and 0xFF) == 0xFF &&
            (rawSample[1].toInt() and 0xF6) == 0xF0
        ) {
            val encodedLen = (((rawSample[3].toInt() and 0x03) shl 11) or
                ((rawSample[4].toInt() and 0xFF) shl 3) or
                ((rawSample[5].toInt() and 0xE0) ushr 5))
            if (encodedLen == rawSample.size) return rawSample
        }

        val csd0 = format.initializationData.firstOrNull()
        var aot = 2
        var freqIdx = AAC_SAMPLE_RATES.indexOfFirst { it == format.sampleRate }.takeIf { it >= 0 } ?: 4
        var chanCfg = format.channelCount.takeIf { it in 1..7 } ?: 2

        if (csd0 != null && csd0.size >= 2) {
            val b0 = csd0[0].toInt() and 0xFF
            val b1 = csd0[1].toInt() and 0xFF
            val parsedAot = (b0 ushr 3) and 0x1F
            val parsedFreqIdx = ((b0 and 0x07) shl 1) or ((b1 ushr 7) and 0x01)
            val parsedChan = (b1 ushr 3) and 0x0F
            // SBR (5) veya PS (29) ise ADTS başlığı temel AAC-LC (2) taşır
            aot = if (parsedAot in 1..4) parsedAot else 2
            if (parsedFreqIdx in 0..12) freqIdx = parsedFreqIdx
            if (parsedChan in 1..7) chanCfg = parsedChan
        }

        val profile = (aot - 1).coerceIn(0, 3)
        val frameLength = rawSample.size + 7
        val out = ByteArray(frameLength)
        out[0] = 0xFF.toByte()
        out[1] = 0xF1.toByte()
        out[2] = (((profile shl 6) or (freqIdx shl 2) or (chanCfg ushr 2)) and 0xFF).toByte()
        out[3] = ((((chanCfg and 0x03) shl 6) or ((frameLength ushr 11) and 0x03)) and 0xFF).toByte()
        out[4] = ((frameLength ushr 3) and 0xFF).toByte()
        out[5] = (((frameLength and 0x07) shl 5) or 0x1F).toByte()
        out[6] = 0xFC.toByte()
        System.arraycopy(rawSample, 0, out, 7, rawSample.size)
        return out
    }

    // ── RFC 7845 Ogg Opus Paketleyici (API 26+ ve JVM uyumlu) ────────────────

    internal class OggOpusStreamWriter(
        private val out: OutputStream,
        private val channels: Int,
        private val sampleRate: Int,
        private val opusHead: ByteArray?
    ) {
        private val serialNumber: Int = 0x4B495453 // "KITS"
        private var pageSequence: Int = 0
        private var lastGranulePos: Long = 0L

        fun writeHeaders() {
            val headPacket = if (opusHead != null && opusHead.size >= 19 &&
                String(opusHead, 0, 8, Charsets.US_ASCII) == "OpusHead"
            ) {
                opusHead
            } else {
                ByteBuffer.allocate(19).order(ByteOrder.LITTLE_ENDIAN).apply {
                    put("OpusHead".toByteArray(Charsets.US_ASCII))
                    put(1.toByte()) // version
                    put(channels.coerceIn(1, 8).toByte())
                    putShort(312.toShort()) // pre-skip
                    putInt(sampleRate)
                    putShort(0.toShort()) // output gain
                    put(0.toByte()) // channel mapping family
                }.array()
            }
            writeOggPage(headerType = 0x02, granulePosition = 0L, packet = headPacket)

            val vendor = "Kitsugi".toByteArray(Charsets.UTF_8)
            val tagsPacket = ByteBuffer.allocate(8 + 4 + vendor.size + 4)
                .order(ByteOrder.LITTLE_ENDIAN).apply {
                    put("OpusTags".toByteArray(Charsets.US_ASCII))
                    putInt(vendor.size)
                    put(vendor)
                    putInt(0) // user comment list length
                }.array()
            writeOggPage(headerType = 0x00, granulePosition = 0L, packet = tagsPacket)
        }

        fun writeAudioPacket(packet: ByteArray, timeUs: Long) {
            // Opus granule position her zaman 48 kHz örnek cinsindendir
            val computedGranule = if (timeUs > 0L) (timeUs * 48L) / 1000L + 960L else lastGranulePos + 960L
            lastGranulePos = maxOf(lastGranulePos + 960L, computedGranule)
            writeOggPage(headerType = 0x00, granulePosition = lastGranulePos, packet = packet)
        }

        fun finish() {
            writeOggPage(headerType = 0x04, granulePosition = lastGranulePos, packet = ByteArray(0))
        }

        private fun writeOggPage(headerType: Int, granulePosition: Long, packet: ByteArray) {
            val lacing = ArrayList<Byte>()
            var remaining = packet.size
            if (packet.isNotEmpty()) {
                while (remaining >= 255) {
                    lacing.add(255.toByte())
                    remaining -= 255
                }
                lacing.add(remaining.toByte())
            }
            val headerLen = 27 + lacing.size
            val page = ByteArray(headerLen + packet.size)
            val buf = ByteBuffer.wrap(page).order(ByteOrder.LITTLE_ENDIAN)
            buf.put("OggS".toByteArray(Charsets.US_ASCII))
            buf.put(0.toByte()) // version
            buf.put(headerType.toByte())
            buf.putLong(granulePosition)
            buf.putInt(serialNumber)
            buf.putInt(pageSequence++)
            buf.putInt(0) // CRC yer tutucu (offset 22..25)
            buf.put(lacing.size.toByte())
            for (b in lacing) buf.put(b)
            if (packet.isNotEmpty()) {
                buf.put(packet)
            }
            val crc = computeOggCrc32(page)
            buf.putInt(22, crc)
            out.write(page)
        }

        companion object {
            private val CRC_TABLE = IntArray(256).also { table ->
                for (i in 0 until 256) {
                    var r = i shl 24
                    for (bit in 0 until 8) {
                        r = if ((r and -0x80000000) != 0) {
                            (r shl 1) xor 0x04C11DB7
                        } else {
                            r shl 1
                        }
                    }
                    table[i] = r
                }
            }

            fun computeOggCrc32(data: ByteArray): Int {
                var crc = 0
                for (b in data) {
                    crc = (crc shl 8) xor CRC_TABLE[((crc ushr 24) xor (b.toInt() and 0xFF)) and 0xFF]
                }
                return crc
            }
        }
    }

    internal fun buildWavHeader(
        pcmDataSize: Long,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ): ByteArray {
        val byteRate = sampleRate * channels * (bitsPerSample / 8)
        val blockAlign = channels * (bitsPerSample / 8)
        val riffChunkSize = (36L + pcmDataSize).coerceAtMost(0xFFFFFFFFL).toInt()
        val dataChunkSize = pcmDataSize.coerceAtMost(0xFFFFFFFFL).toInt()
        return ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(riffChunkSize)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16) // PCM fmt chunk size
            putShort(1.toShort()) // AudioFormat = 1 (PCM)
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort(blockAlign.toShort())
            putShort(bitsPerSample.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataChunkSize)
        }.array()
    }

    // ── Media3 Akışsal (Streaming) Extractor Altyapısı ───────────────────────

    internal fun streamSingleTrackSamples(
        file: File,
        targetTrackId: Int,
        onSample: (Format?, ByteArray, Long, Int) -> Unit
    ) {
        streamMultipleTracksSamples(file, setOf(targetTrackId)) { _, fmt, bytes, timeUs, flags ->
            onSample(fmt, bytes, timeUs, flags)
        }
    }

    private fun streamMultipleTracksSamples(
        file: File,
        targetTrackIds: Set<Int>,
        onSample: (Int, Format?, ByteArray, Long, Int) -> Unit
    ) {
        RandomAccessFile(file, "r").use { raf ->
            val extractor = selectMatchingExtractor(raf)
                ?: throw IOException("Dosya kabı için uygun Media3 Extractor bulunamadı: ${file.name}")
            try {
                val streamingOutput = StreamingExtractorOutput(targetTrackIds, onSample)
                extractor.init(streamingOutput)
                var input = createExtractorInput(raf, 0L)
                val holder = PositionHolder()
                while (true) {
                    val res = extractor.read(input, holder)
                    when (res) {
                        Extractor.RESULT_CONTINUE -> Unit
                        Extractor.RESULT_SEEK -> {
                            input = createExtractorInput(raf, holder.position)
                        }
                        Extractor.RESULT_END_OF_INPUT -> break
                    }
                }
            } finally {
                runCatching { extractor.release() }
            }
        }
    }

    private fun selectMatchingExtractor(raf: RandomAccessFile): Extractor? {
        val factory = DefaultExtractorsFactory()
            .setSubtitleParserFactory(SubtitleParser.Factory.UNSUPPORTED)
            .setTextTrackTranscodingEnabled(false)
        val candidates = factory.createExtractors()
        for (candidate in candidates) {
            raf.seek(0L)
            val input = createExtractorInput(raf, 0L)
            val matched = try {
                candidate.sniff(input)
            } catch (_: EOFException) {
                false
            } catch (_: Exception) {
                false
            }
            if (matched) {
                raf.seek(0L)
                return candidate
            }
            runCatching { candidate.release() }
        }
        raf.seek(0L)
        return null
    }

    private fun createExtractorInput(raf: RandomAccessFile, position: Long): DefaultExtractorInput {
        val totalLen = raf.length()
        raf.seek(position)
        val reader = DataReader { buffer, offset, length ->
            val read = raf.read(buffer, offset, length)
            if (read == -1) C.RESULT_END_OF_INPUT else read
        }
        return DefaultExtractorInput(reader, position, totalLen)
    }

    private fun copyFromOffset(src: File, dst: File, startOffset: Long) {
        RandomAccessFile(src, "r").use { raf ->
            raf.seek(startOffset.coerceIn(0L, raf.length()))
            BufferedOutputStream(FileOutputStream(dst)).use { out ->
                val buf = ByteArray(64 * 1024)
                var read: Int
                while (raf.read(buf).also { read = it } != -1) {
                    out.write(buf, 0, read)
                }
                out.flush()
            }
        }
    }

    private class ProbingTrackHolder : TrackOutput {
        var format: Format? = null
        private val discard = DiscardingTrackOutput()

        override fun format(format: Format) {
            this.format = format
        }

        override fun sampleData(
            input: DataReader,
            length: Int,
            allowEndOfInput: Boolean,
            sampleDataPart: Int
        ): Int = discard.sampleData(input, length, allowEndOfInput, sampleDataPart)

        override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) {
            discard.sampleData(data, length, sampleDataPart)
        }

        override fun sampleMetadata(
            timeUs: Long,
            flags: Int,
            size: Int,
            offset: Int,
            cryptoData: TrackOutput.CryptoData?
        ) = Unit
    }

    private class ProbingExtractorOutput : ExtractorOutput {
        val tracks = LinkedHashMap<Int, ProbingTrackHolder>()
        var tracksEnded = false

        override fun track(id: Int, type: Int): TrackOutput =
            tracks.getOrPut(id) { ProbingTrackHolder() }

        override fun endTracks() {
            tracksEnded = true
        }

        override fun seekMap(seekMap: SeekMap) = Unit

        fun isReady(): Boolean =
            tracksEnded && tracks.isNotEmpty() && tracks.values.all { it.format != null }
    }

    private class StreamingTrackCollector(
        private val trackId: Int,
        private val callback: (Int, Format?, ByteArray, Long, Int) -> Unit
    ) : TrackOutput {
        private var currentFormat: Format? = null
        private val accumulator = ByteArrayOutputStream(8192)

        override fun format(format: Format) {
            currentFormat = format
        }

        override fun sampleData(
            input: DataReader,
            length: Int,
            allowEndOfInput: Boolean,
            sampleDataPart: Int
        ): Int {
            val chunk = ByteArray(minOf(length, 64 * 1024))
            val bytesRead = input.read(chunk, 0, chunk.size)
            if (bytesRead == C.RESULT_END_OF_INPUT) {
                if (allowEndOfInput) return C.RESULT_END_OF_INPUT
                throw EOFException()
            }
            if (bytesRead > 0) {
                accumulator.write(chunk, 0, bytesRead)
            }
            return bytesRead
        }

        override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) {
            if (length <= 0) return
            val chunk = ByteArray(length)
            data.readBytes(chunk, 0, length)
            accumulator.write(chunk, 0, length)
        }

        override fun sampleMetadata(
            timeUs: Long,
            flags: Int,
            size: Int,
            offset: Int,
            cryptoData: TrackOutput.CryptoData?
        ) {
            val buffered = accumulator.toByteArray()
            accumulator.reset()
            if (size <= 0 || buffered.isEmpty()) return
            if (offset == 0 && size == buffered.size) {
                callback(trackId, currentFormat, buffered, timeUs, flags)
            } else if (size <= buffered.size) {
                val sample = buffered.copyOfRange(0, size)
                val remainderStart = size
                if (remainderStart < buffered.size) {
                    accumulator.write(buffered, remainderStart, buffered.size - remainderStart)
                }
                callback(trackId, currentFormat, sample, timeUs, flags)
            } else {
                callback(trackId, currentFormat, buffered, timeUs, flags)
            }
        }
    }

    private class StreamingExtractorOutput(
        private val targetTrackIds: Set<Int>,
        private val callback: (Int, Format?, ByteArray, Long, Int) -> Unit
    ) : ExtractorOutput {
        private val collectors = HashMap<Int, TrackOutput>()
        private val discard = DiscardingTrackOutput()

        override fun track(id: Int, type: Int): TrackOutput {
            if (id !in targetTrackIds) return discard
            return collectors.getOrPut(id) { StreamingTrackCollector(id, callback) }
        }

        override fun endTracks() = Unit
        override fun seekMap(seekMap: SeekMap) = Unit
    }
}
