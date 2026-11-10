package com.kitsugi.animelist.core.player

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** Convert legacy text subtitles to UTF-8 without damaging already-UTF-8 or binary files. */
object SubtitleEncoding {
    fun toUtf8(bytes: ByteArray, contentType: String? = null, language: String? = null): ByteArray {
        val utf16 = bytes.size >= 2 && ((bytes[0] == 0xff.toByte() && bytes[1] == 0xfe.toByte()) ||
            (bytes[0] == 0xfe.toByte() && bytes[1] == 0xff.toByte()))
        if (utf16) return decode(bytes, "UTF-16")?.toByteArray(Charsets.UTF_8) ?: bytes
        // NUL-containing non-UTF-16 content may be a binary subtitle (e.g. VobSub).
        if (bytes.contains(0.toByte())) return bytes
        if (decode(bytes, "UTF-8") != null) return bytes

        val lang = language.orEmpty().trim().lowercase().substringBefore('-').substringBefore('_')
        val turkish = lang in setOf("tr", "tur", "turkish", "türkçe", "turkce")
        val declared = Regex("(?i)charset\\s*=\\s*[\\\"']?([^;\\s\\\"']+)")
            .find(contentType.orEmpty())?.groupValues?.get(1)
        val fallback = when (lang) {
            "tr", "tur", "turkish", "türkçe", "turkce" -> "windows-1254"
            "ru", "rus", "russian", "uk", "ukr", "bul", "bg" -> "windows-1251"
            "ar", "ara", "arabic", "fa", "fas", "per" -> "windows-1256"
            "el", "gre", "ell", "greek" -> "windows-1253"
            "he", "heb", "hebrew" -> "windows-1255"
            else -> "windows-1252"
        }
        val charset = declared?.takeUnless {
            it.equals("utf-8", true) || it.equals("utf8", true) ||
                (turkish && it.equals("iso-8859-1", true))
        } ?: fallback
        return (decode(bytes, charset) ?: decode(bytes, fallback))?.toByteArray(Charsets.UTF_8) ?: bytes
    }

    private fun decode(bytes: ByteArray, charset: String): String? = try {
        Charset.forName(charset).newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    } catch (_: Exception) { null }
}
