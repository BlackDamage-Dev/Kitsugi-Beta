package com.kitsugi.animelist.data.ambient

import java.util.Locale

/** Canonical audio container/extension handling shared by validation, playback, and downloads. */
internal object AmbientAudioFormats {
    val supportedPlaybackTypes: Set<String> = setOf(
        "mp3", "ogg", "oga", "opus", "flac", "m4a", "m4b", "aac", "wav", "aif", "aiff",
        "alac", "amr", "3gp", "mka", "webm", "ape", "wv", "tta", "caf", "au", "wma",
        "ac3", "eac3", "m3u8", "hls",
    )

    private val mimeToType = mapOf(
        "audio/mpeg" to "mp3",
        "audio/mp3" to "mp3",
        "audio/ogg" to "ogg",
        "application/ogg" to "ogg",
        "audio/opus" to "opus",
        "audio/flac" to "flac",
        "audio/x-flac" to "flac",
        "audio/mp4" to "m4a",
        "audio/m4a" to "m4a",
        "audio/aac" to "aac",
        "audio/aacp" to "aac",
        "audio/wav" to "wav",
        "audio/x-wav" to "wav",
        "audio/wave" to "wav",
        "audio/aiff" to "aiff",
        "audio/x-aiff" to "aiff",
        "audio/3gpp" to "3gp",
        "audio/amr" to "amr",
        "audio/x-ms-wma" to "wma",
        "audio/x-matroska" to "mka",
        "audio/matroska" to "mka",
        "audio/ac3" to "ac3",
        "audio/eac3" to "eac3",
        "audio/webm" to "webm",
        "audio/ape" to "ape",
        "audio/wavpack" to "wv",
        "audio/tta" to "tta",
        "audio/x-caf" to "caf",
        "audio/basic" to "au",
        "application/vnd.apple.mpegurl" to "m3u8",
        "application/x-mpegurl" to "m3u8",
    )

    /** Accepts file extensions or MIME strings and returns a bounded, canonical extension. */
    fun canonicalType(value: String): String {
        val normalized = value.trim().lowercase(Locale.ROOT).substringBefore(';').take(100)
        return mimeToType[normalized]
            ?: normalized.substringAfterLast('/').removePrefix("x-").takeIf { it in supportedPlaybackTypes }
            ?: normalized.substringBefore('?').substringAfterLast('.').takeIf { it in supportedPlaybackTypes }
            ?: ""
    }

    fun mimeType(value: String): String = when (canonicalType(value)) {
        "mp3" -> "audio/mpeg"
        "ogg", "oga" -> "audio/ogg"
        "opus" -> "audio/opus"
        "flac" -> "audio/flac"
        "m4a", "m4b", "alac" -> "audio/mp4"
        "aac" -> "audio/aac"
        "wav" -> "audio/wav"
        "aif", "aiff" -> "audio/aiff"
        "amr" -> "audio/amr"
        "3gp" -> "audio/3gpp"
        "mka" -> "audio/x-matroska"
        "wma" -> "audio/x-ms-wma"
        "ac3" -> "audio/ac3"
        "eac3" -> "audio/eac3"
        "webm" -> "audio/webm"
        "ape" -> "audio/ape"
        "wv" -> "audio/wavpack"
        "tta" -> "audio/tta"
        "caf" -> "audio/x-caf"
        "au" -> "audio/basic"
        "m3u8", "hls" -> "application/vnd.apple.mpegurl"
        else -> "application/octet-stream"
    }

    fun fileExtension(value: String): String = when (val type = canonicalType(value)) {
        "hls" -> "m3u8"
        "oga" -> "ogg"
        "aif" -> "aiff"
        else -> type
    }

    /** Formats most commonly readable by Android system players without a conversion step. */
    val widelySupportedTypes = setOf("mp3", "m4a", "m4b", "aac", "wav", "3gp", "amr")

    fun isDirectAudioFile(value: String): Boolean = canonicalType(value) in supportedPlaybackTypes &&
        canonicalType(value) !in setOf("m3u8", "hls")

    /** Decide whether to attempt AAC normalization; unknown device support never claims success. */
    fun shouldAttemptAacConversion(value: String, systemDecoderAvailable: Boolean?): Boolean {
        val type = canonicalType(value)
        return type !in widelySupportedTypes || systemDecoderAvailable == false
    }
}

/** Small, dependency-free HTML cleanup for Wikimedia Commons metadata fields. */
internal fun stripAmbientAudioHtml(value: String): String {
    val bounded = value.cappedAmbientAudioValue()
        .replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ")
        .replace(Regex("(?i)<br\\s*/?>|</p\\s*>|</div\\s*>"), " ")
        .replace(Regex("(?s)<[^>]{0,1000}>"), " ")
    val named = bounded
        .replace("&amp;", "&", ignoreCase = true)
        .replace("&lt;", "<", ignoreCase = true)
        .replace("&gt;", ">", ignoreCase = true)
        .replace("&quot;", "\"", ignoreCase = true)
        .replace("&#39;", "'", ignoreCase = true)
        .replace("&apos;", "'", ignoreCase = true)
        .replace("&nbsp;", " ", ignoreCase = true)
    return Regex("&#(x[0-9a-fA-F]+|[0-9]+);").replace(named) { match ->
        val raw = match.groupValues[1]
        val codePoint = if (raw.startsWith("x", ignoreCase = true)) {
            raw.drop(1).toIntOrNull(16)
        } else {
            raw.toIntOrNull()
        }
        codePoint?.takeIf { Character.isValidCodePoint(it) }?.let { String(Character.toChars(it)) } ?: " "
    }.replace(Regex("\\s+"), " ").trim().cappedAmbientAudioValue()
}
