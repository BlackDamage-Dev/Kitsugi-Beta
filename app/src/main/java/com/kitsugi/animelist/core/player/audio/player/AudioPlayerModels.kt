package com.kitsugi.animelist.core.player.audio.player

import java.util.Locale

enum class AudioRepeatMode {
    OFF,
    ALL,
    ONE;

    fun next(): AudioRepeatMode = when (this) {
        OFF -> ALL
        ALL -> ONE
        ONE -> OFF
    }
}

enum class AudioBackendKind(val shortLabel: String) {
    MEDIA3_FFMPEG("Media3·FFmpeg"),
    MPV_FFMPEG("MPV·FFmpeg")
}

data class AudioStreamSpecs(
    val codec: String? = null,
    val sampleRateHz: Int = 0,
    val channelCount: Int = 0,
    val bitrateKbps: Int = 0,
    val container: String? = null
) {
    fun summaryBadges(fallbackFormat: String? = null): List<String> = buildList {
        val primary = AudioFormatSupport.normalizeCodecLabel(codec)
            ?: fallbackFormat?.trim()?.uppercase(Locale.ROOT)?.takeIf { it.isNotBlank() }
        if (primary != null) add(primary)
        val containerUpper = container?.trim()?.uppercase(Locale.ROOT)?.takeIf { it.isNotBlank() }
        val primaryAlphaNum = primary?.filter(Char::isLetterOrDigit)?.uppercase(Locale.ROOT)
        val containerAlphaNum = containerUpper?.filter(Char::isLetterOrDigit)?.uppercase(Locale.ROOT)
        if (containerUpper != null && containerAlphaNum != primaryAlphaNum) {
            add(containerUpper)
        }
        AudioFormatSupport.formatSampleRate(sampleRateHz)?.let { add(it) }
        AudioFormatSupport.formatChannels(channelCount)?.let { add(it) }
        if (bitrateKbps > 0) add("$bitrateKbps kbps")
    }
}

data class AudioPlayerItem(
    val id: String,
    val uriString: String,
    val filePath: String? = null,
    val title: String,
    val subtitle: String? = null,
    val animeTitle: String? = null,
    val episode: Int? = null,
    val trackLabel: String? = null,
    val formatBadge: String = "AUDIO",
    val mimeType: String? = null,
    val sizeBytes: Long = 0L,
    val durationMs: Long = 0L,
    val artworkUrlOrPath: String? = null
)

data class AudioPlayerState(
    val queue: List<AudioPlayerItem> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val preservePitch: Boolean = true,
    val volumeBoostDb: Float = 0f,
    val repeatMode: AudioRepeatMode = AudioRepeatMode.OFF,
    val shuffleEnabled: Boolean = false,
    val shuffleOrder: List<Int> = emptyList(),
    val backendKind: AudioBackendKind = AudioBackendKind.MEDIA3_FFMPEG,
    val streamSpecs: AudioStreamSpecs = AudioStreamSpecs(),
    val sleepTimerRemainingMs: Long? = null,
    val sleepStopAtTrackEnd: Boolean = false,
    val isExpanded: Boolean = false,
    val errorMessage: String? = null,
    val audioSessionId: Int = 0
) {
    val currentItem: AudioPlayerItem?
        get() = queue.getOrNull(currentIndex)

    val isActive: Boolean
        get() = currentItem != null
}

object AudioFormatSupport {

    val ALL_AUDIO_EXTENSIONS: Set<String> = setOf(
        "mp3", "m4a", "mp4a", "m4b", "m4r", "aac", "flac", "wav", "wave",
        "ogg", "oga", "opus", "spx", "webm", "weba", "mka",
        "ac3", "eac3", "ec3", "ac4", "dts", "dtshd", "thd", "mlp",
        "amr", "awb", "3ga", "wma", "asf", "ape", "wv", "mpc", "tta", "tak",
        "alac", "aiff", "aif", "aifc", "caf", "pcm", "dsf", "dff",
        "mp2", "mp1", "mpa", "mid", "midi"
    )

    val STRICT_AUDIO_EXTENSIONS: Set<String> = ALL_AUDIO_EXTENSIONS - setOf("webm", "asf")

    /**
     * Media3'ün yerleşik extractor/decoder katmanında doğrudan çözülmeyen ya da kararsız kalan,
     * bu nedenle gecikmesiz doğrudan `libmpv` (FFmpeg `libavformat` + `libavcodec`) ses motoruna
     * yönlendirilen egzotik konteyner ve kodek uzantıları.
     */
    val MPV_PREFERRED_EXTENSIONS: Set<String> = setOf(
        "wma", "asf", "ape", "wv", "mpc", "tta", "tak",
        "dts", "dtshd", "thd", "mlp",
        "caf", "aiff", "aif", "aifc", "dsf", "dff"
    )

    fun extractExtension(nameOrUri: String): String {
        val clean = nameOrUri
            .substringBefore('?')
            .substringBefore('#')
            .substringAfterLast('/')
        return if ('.' in clean) clean.substringAfterLast('.', "").lowercase(Locale.ROOT) else ""
    }

    fun isAudioFileName(nameOrUri: String, allowWebmAsAudio: Boolean = false): Boolean {
        val ext = extractExtension(nameOrUri)
        if (ext.isEmpty()) return false
        return if (allowWebmAsAudio) ext in ALL_AUDIO_EXTENSIONS else ext in STRICT_AUDIO_EXTENSIONS
    }

    fun isAudioMimeType(mime: String?): Boolean {
        val normalized = mime?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() } ?: return false
        return normalized.startsWith("audio/") ||
            normalized == "application/ogg" ||
            normalized == "application/x-ogg" ||
            normalized == "application/flac" ||
            normalized == "application/x-flac"
    }

    fun prefersMpvDirectly(nameOrUri: String, mimeType: String? = null): Boolean {
        val ext = extractExtension(nameOrUri)
        if (ext in MPV_PREFERRED_EXTENSIONS) return true
        val mime = mimeType?.trim()?.lowercase(Locale.ROOT).orEmpty()
        return mime.contains("x-ms-wma") ||
            mime.contains("ape") ||
            mime.contains("wavpack") ||
            mime.contains("musepack") ||
            mime.contains("aiff") ||
            mime.contains("x-caf") ||
            mime.contains("vnd.dts") ||
            mime.contains("truehd") ||
            mime.contains("dsd")
    }

    fun normalizeCodecLabel(raw: String?): String? {
        val s = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val lower = s.lowercase(Locale.ROOT)
        return when {
            lower == "audio/mp4a-latm" || lower.startsWith("mp4a") || lower == "aac" -> "AAC"
            lower == "audio/mpeg" || lower == "mp3" -> "MP3"
            lower == "audio/eac3" || lower == "audio/eac3-joc" || lower == "eac3" || lower == "ec-3" -> "E-AC-3"
            lower == "audio/ac3" || lower == "ac3" || lower == "ac-3" -> "AC-3"
            lower == "audio/ac4" || lower == "ac4" -> "AC-4"
            lower == "audio/flac" || lower == "flac" -> "FLAC"
            lower == "audio/alac" || lower == "alac" -> "ALAC"
            lower == "audio/opus" || lower == "opus" -> "OPUS"
            lower == "audio/vorbis" || lower == "vorbis" -> "VORBIS"
            lower == "audio/raw" || lower.startsWith("pcm") || lower == "audio/wav" || lower == "audio/x-wav" -> "PCM"
            lower.contains("dts") -> "DTS"
            lower.contains("truehd") || lower == "mlp" -> "TrueHD"
            lower.contains("wma") -> "WMA"
            lower == "ape" -> "APE"
            lower == "wavpack" || lower == "wv" -> "WavPack"
            lower.startsWith("audio/") -> lower.removePrefix("audio/").uppercase(Locale.ROOT)
            else -> s.uppercase(Locale.ROOT)
        }
    }

    fun formatSampleRate(hz: Int): String? {
        if (hz <= 0) return null
        val khz = hz / 1000.0
        return if (hz % 1000 == 0) {
            "${hz / 1000} kHz"
        } else {
            String.format(Locale.US, "%.1f kHz", khz)
        }
    }

    fun formatChannels(channels: Int): String? = when {
        channels <= 0 -> null
        channels == 1 -> "Mono"
        channels == 2 -> "Stereo"
        channels == 6 -> "5.1"
        channels == 8 -> "7.1"
        else -> "$channels Ch"
    }

    fun formatDuration(ms: Long): String {
        val safeSeconds = (ms.coerceAtLeast(0L)) / 1000L
        val hours = safeSeconds / 3600L
        val minutes = (safeSeconds % 3600L) / 60L
        val seconds = safeSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun resolveNextIndex(
        currentIndex: Int,
        queueSize: Int,
        repeatMode: AudioRepeatMode,
        shuffleOrder: List<Int>?,
        autoAdvance: Boolean
    ): Int? {
        if (queueSize <= 0 || currentIndex !in 0 until queueSize) return null
        if (autoAdvance && repeatMode == AudioRepeatMode.ONE) return currentIndex

        val order = shuffleOrder?.takeIf { it.size == queueSize } ?: (0 until queueSize).toList()
        val posInOrder = order.indexOf(currentIndex).takeIf { it >= 0 } ?: currentIndex
        return when {
            posInOrder + 1 < order.size -> order[posInOrder + 1]
            repeatMode == AudioRepeatMode.ALL || (!autoAdvance && repeatMode == AudioRepeatMode.ONE) ->
                order.firstOrNull()
            else -> null
        }
    }

    fun resolvePrevIndex(
        currentIndex: Int,
        queueSize: Int,
        repeatMode: AudioRepeatMode,
        shuffleOrder: List<Int>?
    ): Int? {
        if (queueSize <= 0 || currentIndex !in 0 until queueSize) return null
        val order = shuffleOrder?.takeIf { it.size == queueSize } ?: (0 until queueSize).toList()
        val posInOrder = order.indexOf(currentIndex).takeIf { it >= 0 } ?: currentIndex
        return when {
            posInOrder - 1 >= 0 -> order[posInOrder - 1]
            repeatMode == AudioRepeatMode.ALL || repeatMode == AudioRepeatMode.ONE ->
                order.lastOrNull()
            else -> order.firstOrNull()
        }
    }
}
