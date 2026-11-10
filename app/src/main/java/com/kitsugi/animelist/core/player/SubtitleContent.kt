package com.kitsugi.animelist.core.player

/** Reject common error pages and unsupported archives before handing a file to the player. */
object SubtitleContent {
    fun isUsable(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        if (bytes.size >= 2 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4b.toByte()) return false
        if (bytes.size >= 4 && bytes[0] == 'R'.code.toByte() && bytes[1] == 'a'.code.toByte() &&
            bytes[2] == 'r'.code.toByte() && bytes[3] == '!'.code.toByte()) return false
        val prefix = bytes.take(512).toByteArray().toString(Charsets.UTF_8)
            .trimStart('\uFEFF', ' ', '\n', '\r', '\t').lowercase()
        return !prefix.startsWith("<!doctype html") && !prefix.startsWith("<html") &&
            !prefix.startsWith("{\"error\"")
    }
}
