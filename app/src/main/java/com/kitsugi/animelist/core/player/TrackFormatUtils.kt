package com.kitsugi.animelist.core.player

import java.util.Locale

/**
 * Motorlardan gelen codec/MIME bilgilerini ve harici dosya adlarını kullanıcıya
 * gösterilecek kısa, tutarlı format etiketlerine çevirir.
 *
 * Buradaki öncelik sırası önemlidir: codec/MIME gerçek parçayı, dosya uzantısı ise
 * yalnızca taşıyıcıyı anlatır. Bu yüzden bilinmeyen bir URL varsayılan olarak SRT
 * veya VTT sayılmaz. ASS/SSA ve TTML/DFXP gibi aynı codec'i paylaşan biçimlerde
 * uzantı yalnızca alt türü ayırt etmek için kullanılır.
 */
object TrackFormatUtils {

    fun subtitleFormat(
        codec: String? = null,
        mimeType: String? = null,
        source: String? = null,
        declaredFormat: String? = null,
    ): String? {
        val codecToken = normalize(codec)
        val mimeToken = normalize(mimeType)
        val declaredToken = normalize(declaredFormat)
        val extension = knownExtension(declaredFormat) ?: knownExtension(source)

        // Advanced SubStation Alpha ve SubStation Alpha, MPV/Media3 tarafından çoğu
        // zaman aynı "ass" codec'i ile bildirilir. Uzantı varsa gerçek alt türü göster.
        val hasEngineFormat = codecToken.isNotEmpty() || mimeToken.isNotEmpty()
        val assSignaled = codecToken.isAss() || mimeToken.isAss() ||
            (!hasEngineFormat && (declaredToken.isAss() || extension in setOf("ass", "ssa")))
        if (assSignaled) {
            return when {
                extension == "ssa" || codecToken == "ssa" || declaredToken == "ssa" -> "SSA"
                extension == "ass" || codecToken == "ass" || declaredToken == "ass" -> "ASS"
                else -> "ASS/SSA"
            }
        }

        subtitleFromCodec(codecToken)?.let { return it }
        subtitleFromCodec(mimeToken)?.let { return it }
        subtitleFromCodec(declaredToken)?.let { return it }
        subtitleFromExtension(extension)?.let { return it }
        // Yeni/nadir codec'lerde bile motorun gerçek bildirimini gizleme.
        return rawCodecTag(codec)
    }

    fun audioFormat(
        codec: String? = null,
        mimeType: String? = null,
        source: String? = null,
        declaredFormat: String? = null,
    ): String? {
        // Codec listesi bazen "mp4a.40.2,mp4a.40.5" gibi birden fazla değer taşır.
        // En belirgin profili önce denemek yanlışlıkla yalnızca AAC/MP4 göstermeyi önler.
        val candidates = listOf(codec, mimeType, declaredFormat)
            .flatMap { value -> value?.split(',') ?: emptyList() }
            .map(::normalize)
            .filter { it.isNotEmpty() }

        candidates.forEach { token -> audioFromCodec(token)?.let { return it } }
        val extension = knownExtension(declaredFormat) ?: knownExtension(source)
        audioFromExtension(extension)?.let { return it }
        return rawCodecTag(codec)
    }

    private fun rawCodecTag(codec: String?): String? {
        val value = codec?.split(',')?.firstOrNull()?.trim().orEmpty()
        if (value.isBlank() || value.equals("unknown", ignoreCase = true) ||
            value.equals("none", ignoreCase = true) || value.equals("null", ignoreCase = true)
        ) return null
        return value.takeIf { it.length <= 32 }?.uppercase(Locale.ROOT)
    }

    /** URL, file/content URI veya düz dosya yolundan bilinen uzantıyı çıkarır. */
    fun knownExtension(source: String?): String? {
        if (source.isNullOrBlank()) return null
        // Eklentiler bazen yalnızca "srt" / "m4a" gibi düz bir biçim adı gönderir.
        val direct = source.trim().removePrefix(".").lowercase(Locale.ROOT)
        if (direct in SUBTITLE_EXTENSIONS || direct in AUDIO_EXTENSIONS) return direct
        val clean = source
            .substringBefore('#')
            .substringBefore('?')
            .removeSuffix("/")
        val fileName = clean.substringAfterLast('/').substringAfterLast('\\')
        val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (extension.isBlank() || extension.length > 10 || !extension.all { it.isLetterOrDigit() }) return null
        return if (extension in SUBTITLE_EXTENSIONS || extension in AUDIO_EXTENSIONS) extension else null
    }

    private fun subtitleFromCodec(token: String): String? = when {
        token.isBlank() || token.isAss() -> null
        "subrip" in token || token == "srt" || token == "application/x-subrip" || token == "application/subrip" -> "SRT"
        "webvtt" in token || token == "vtt" || token == "text/vtt" -> "VTT"
        "ttml" in token || token == "application/ttml+xml" -> if (knownExtension(token) == "dfxp") "DFXP" else "TTML"
        "dfxp" in token -> "DFXP"
        "microdvd" in token -> "MicroDVD"
        "hdmv_pgs" in token || "pgs_subtitle" in token || token == "pgs" -> "PGS"
        "dvd_subtitle" in token || "dvdsub" in token || "vobsub" in token -> "VobSub"
        "dvb_subtitle" in token || "dvbsub" in token -> "DVB"
        "teletext" in token -> "Teletext"
        "mov_text" in token || "tx3g" in token -> "MOV Text"
        "sami" in token || token == "smi" -> "SAMI"
        "realtext" in token -> "RealText"
        "jacosub" in token || token == "jss" -> "JacoSub"
        "subviewer" in token -> "SubViewer"
        "xsub" in token -> "XSUB"
        token == "sbv" -> "SBV"
        token == "lrc" -> "LRC"
        token == "mpl2" -> "MPL2"
        token == "pjs" -> "PJS"
        token == "stl" -> "STL"
        token == "scc" -> "SCC"
        token == "mks" -> "MKS"
        else -> null
    }

    private fun subtitleFromExtension(extension: String?): String? = when (extension) {
        "srt" -> "SRT"
        "vtt" -> "VTT"
        "ttml" -> "TTML"
        "dfxp" -> "DFXP"
        "sub" -> "SUB"
        "idx" -> "VobSub"
        "sup" -> "PGS"
        "smi", "sami" -> "SAMI"
        "rt" -> "RealText"
        "jss", "js" -> "JacoSub"
        "mpl", "mpl2" -> "MPL2"
        "mks" -> "MKS"
        else -> extension?.takeIf { it in SUBTITLE_EXTENSIONS }?.uppercase(Locale.ROOT)
    }

    private fun audioFromCodec(token: String): String? = when {
        token.isBlank() -> null
        "eac3-joc" in token || "eac3joc" in token -> "E-AC-3 JOC"
        "eac3" in token || "e-ac-3" in token || token == "ec-3" || "audio/ec3" in token -> "E-AC-3"
        "truehd" in token || "mlp.fba" in token -> "TrueHD"
        "dts-hd" in token || "dtshd" in token || "dtsh" in token || "dtsl" in token -> "DTS-HD"
        "dts:x" in token || "dtsx" in token -> "DTS:X"
        "dts express" in token || "dtse" in token -> "DTS Express"
        token == "dts" || token == "dca" || "audio/vnd.dts" in token || "audio/dts" in token || token == "dtsc" -> "DTS"
        "ac-4" in token || token == "ac4" -> "AC-4"
        "ac-3" in token || token == "ac3" || "audio/ac3" in token -> "AC-3"
        "aac_latm" in token || "aac-latm" in token || "loas" in token -> "AAC-LATM"
        token == "aac" || "mp4a.40" in token || "audio/aac" in token || "mp4a-latm" in token -> "AAC"
        "mp3" in token || "mpeg.layer.iii" in token || "audio/mpeg" in token -> "MP3"
        token == "mp2" || "mp4a.69" in token || "mp4a.6b" in token -> "MP2"
        "flac" in token -> "FLAC"
        "opus" in token -> "Opus"
        "vorbis" in token -> "Vorbis"
        "alac" in token -> "ALAC"
        "amr-wb" in token || "amrwb" in token || "samr" in token -> "AMR-WB"
        token == "amr" || "amr-nb" in token || "amrnb" in token -> "AMR-NB"
        "wma pro" in token || "wmapro" in token -> "WMA Pro"
        "wma lossless" in token || "wmalossless" in token -> "WMA Lossless"
        token == "wma" || "wmav1" in token || "wmav2" in token || "audio/x-ms-wma" in token -> "WMA"
        "monkey" in token || token == "ape" -> "APE"
        "pcm" in token || "lpcm" in token || "audio/raw" in token || "audio/x-raw" in token -> "PCM"
        "adpcm" in token -> "ADPCM"
        "atrac" in token -> "ATRAC"
        "dsd" in token || token == "dsf" -> "DSD"
        "midi" in token -> "MIDI"
        else -> null
    }

    private fun audioFromExtension(extension: String?): String? = when (extension) {
        "aac" -> "AAC"
        "mp3" -> "MP3"
        "ac3" -> "AC-3"
        "ec3", "eac3" -> "E-AC-3"
        "dts" -> "DTS"
        "dtshd", "dts-hd" -> "DTS-HD"
        "thd", "truehd" -> "TrueHD"
        "mlp" -> "MLP"
        "wav", "wave", "pcm" -> "PCM"
        "wma" -> "WMA"
        "ape" -> "APE"
        "amr" -> "AMR-NB"
        "awb" -> "AMR-WB"
        "ac4" -> "AC-4"
        "dsf", "dsd" -> "DSD"
        "midi", "mid" -> "MIDI"
        "oga" -> "Ogg Audio"
        "m4a", "m4b" -> extension.uppercase(Locale.ROOT)
        "mka" -> "MKA"
        else -> extension?.takeIf { it in AUDIO_EXTENSIONS }?.uppercase(Locale.ROOT)
    }

    /**
     * Harici altyazının ilk baytlarından gerçek metin biçimini tanır.
     * Dönen değer dosya uzantısı olarak kullanılabilecek küçük harfli bir biçimdir.
     */
    fun detectSubtitleContentFormat(bytes: ByteArray): String? {
        if (bytes.isEmpty()) return null
        val sample = bytes.copyOfRange(0, minOf(bytes.size, 64 * 1024))
        val text = when {
            sample.startsWith(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())) ->
                String(sample, 3, sample.size - 3, Charsets.UTF_8)
            sample.startsWith(byteArrayOf(0xFF.toByte(), 0xFE.toByte())) ->
                String(sample, 2, sample.size - 2, Charsets.UTF_16LE)
            sample.startsWith(byteArrayOf(0xFE.toByte(), 0xFF.toByte())) ->
                String(sample, 2, sample.size - 2, Charsets.UTF_16BE)
            else -> String(sample, Charsets.UTF_8)
        }.trimStart('\uFEFF', ' ', '\t', '\r', '\n')

        if (text.isBlank() || text.indexOf('\u0000') >= 0) return null
        val upper = text.uppercase(Locale.ROOT)
        if (upper.startsWith("WEBVTT")) return "vtt"
        if (upper.contains("[SCRIPT INFO]") || upper.contains("[V4+ STYLES]") || upper.contains("[V4 STYLES]")) {
            return if (upper.contains("[V4 STYLES]") && !upper.contains("[V4+ STYLES]")) "ssa" else "ass"
        }
        if (Regex("<TT[\\s>]", RegexOption.IGNORE_CASE).containsMatchIn(text) &&
            (upper.contains("TTML") || upper.contains("XMLNS") || upper.contains("</TT>"))
        ) return "ttml"
        if (upper.contains("<SAMI") || (upper.contains("<SYNC") && upper.contains("<BODY"))) return "smi"
        if (Regex("\\{\\d+}\\{\\d+}").containsMatchIn(text)) return "sub"
        if (upper.contains("[INFORMATION]") && upper.contains("[SUBTITLE]")) return "sub"
        if (Regex("(?m)^\\s*\\d+\\s*\\r?\\n\\s*(?:\\d{1,2}:)?\\d{1,2}:\\d{2}[,.]\\d{1,3}\\s*-->").containsMatchIn(text)) return "srt"
        if (Regex("\\d{1,2}:\\d{2}:\\d{2}[,.]\\d{1,3}\\s*,\\s*\\d{1,2}:\\d{2}:\\d{2}[,.]\\d{1,3}").containsMatchIn(text)) return "sbv"
        if (text.contains("-->")) return "srt"
        if (Regex("(?m)^\\s*\\[\\d{2}:\\d{2}(?:[.:]\\d{2,3})?]").containsMatchIn(text)) return "lrc"
        return null
    }

    private fun String?.isAss(): Boolean {
        val value = this.orEmpty()
        return value == "ass" || value == "ssa" || "text/x-ssa" in value || "application/x-ass" in value
    }

    private fun normalize(value: String?): String = value
        ?.trim()
        ?.removePrefix(".")
        ?.lowercase(Locale.ROOT)
        .orEmpty()

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    private val SUBTITLE_EXTENSIONS = setOf(
        "srt", "ass", "ssa", "vtt", "ttml", "dfxp", "sub", "idx", "sup", "smi", "sami",
        "rt", "sbv", "lrc", "mpl", "mpl2", "tmp", "pjs", "jss", "js", "stl", "scc", "cap", "mks", "txt"
    )

    private val AUDIO_EXTENSIONS = setOf(
        "aac", "mp3", "mp2", "ac3", "ec3", "eac3", "dts", "dtshd", "thd", "truehd", "mlp",
        "flac", "opus", "ogg", "oga", "vorbis", "alac", "wav", "wave", "pcm", "wma", "ape",
        "amr", "awb", "ac4", "dsf", "dsd", "m4a", "m4b", "mka", "mid", "midi", "aiff", "aif"
    )
}
