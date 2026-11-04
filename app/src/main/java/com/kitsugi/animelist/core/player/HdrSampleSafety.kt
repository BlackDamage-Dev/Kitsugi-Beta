package com.kitsugi.animelist.core.player

/** Only HEVC NAL units may be sent to the HEVC RPU/SEI rewriter. */
internal object HdrSampleSafety {
    fun isHevcTrack(mime: String?, codecs: String?): Boolean {
        if (mime == "video/hevc") return true
        if (mime != "video/dolby-vision") return false
        val prefix = codecs?.trim()?.substringBefore('.')?.lowercase(java.util.Locale.ROOT) ?: return false
        return prefix == "dvhe" || prefix == "dvh1"
    }
}
