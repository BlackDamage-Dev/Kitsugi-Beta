package com.kitsugi.animelist.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [StreamLabelSanitizer] birim testleri.
 *
 * Kullanıcı raporu (2026-11-01): izleme geçmişinde kaynak satırı
 * `HDFilmCehennemi · Close · .fa-secondary{opacity:.4} DUAL` olarak görünüyordu.
 */
class StreamLabelSanitizerTest {

    @Test
    fun `CSS kural blogu temizlenir`() {
        val cleaned = StreamLabelSanitizer.sanitize("HDFilmCehennemi · Close · .fa-secondary{opacity:.4} DUAL")
        assertFalse("CSS artığı kalmamalı", cleaned.contains("{"))
        assertFalse(cleaned.contains("opacity"))
        assertFalse(cleaned.contains(".fa-secondary"))
        assertTrue(cleaned.startsWith("HDFilmCehennemi"))
        assertTrue(cleaned.endsWith("DUAL"))
    }

    @Test
    fun `HTML etiketleri ve sinif oznitelikleri temizlenir`() {
        val cleaned = StreamLabelSanitizer.sanitize("<span class=\"fa-secondary\">1080p</span> · Tam HD")
        assertEquals("1080p · Tam HD", cleaned)
    }

    @Test
    fun `HTML varliklari cozulur`() {
        assertEquals("A & B", StreamLabelSanitizer.sanitize("A&nbsp;&amp;&nbsp;B"))
    }

    @Test
    fun `gercek basliklar korunur`() {
        assertEquals(
            "Spider-Man.No.Way.Home.1080p",
            StreamLabelSanitizer.sanitize("Spider-Man.No.Way.Home.1080p")
        )
        assertEquals("video.mp4", StreamLabelSanitizer.sanitize("video.mp4"))
    }

    @Test
    fun `tekrar eden parcalar birlestirmede sadeleşir`() {
        val combined = StreamLabelSanitizer.combine(
            "HDFilmCehennemi · Close · .fa-secondary{opacity:.4} DUAL",
            "Rapidrame · .fa-secondary{opacity:.4} DUAL"
        )
        assertEquals("HDFilmCehennemi · Close · DUAL · Rapidrame", combined)
        assertEquals(1, countOccurrences(combined, "DUAL"))
    }

    @Test
    fun `bos ve null girdi bos metin doner`() {
        assertEquals("", StreamLabelSanitizer.sanitize(null))
        assertEquals("", StreamLabelSanitizer.sanitize("   "))
        assertEquals("", StreamLabelSanitizer.combine(null, null))
    }

    @Test
    fun `ses turu etiketi algilanir`() {
        assertEquals("Dual", StreamLabelSanitizer.detectAudioLabel("1080p DUAL"))
        assertEquals("Dublaj", StreamLabelSanitizer.detectAudioLabel("Türkçe Dublaj"))
        assertEquals("Altyazılı", StreamLabelSanitizer.detectAudioLabel("Altyazılı 720p"))
        assertNull(StreamLabelSanitizer.detectAudioLabel("Sadece başlık"))
    }

    @Test
    fun `ses turu kelimeleri etiketten ayiklanir`() {
        assertEquals(
            "Rapidrame",
            StreamLabelSanitizer.stripAudioTokens("Rapidrame · DUAL")
        )
    }

    @Test
    fun `Android ICU regex uyumlulugu - hicbir desen kacirilmamis cift suslu parantezle bitmez`() {
        val regexFields = StreamLabelSanitizer::class.java.declaredFields
            .filter { it.type == Regex::class.java }
        assertTrue("En az bir Regex alanı bulunmalı", regexFields.isNotEmpty())
        for (field in regexFields) {
            field.isAccessible = true
            val regex = field.get(StreamLabelSanitizer) as Regex
            val pattern = regex.pattern
            assertFalse(
                "${field.name} deseni Android ICU'da PatternSyntaxException fırlatan kaçırılmamış '}}' içeriyor: $pattern",
                pattern.endsWith("}}") && !pattern.endsWith("\\}")
            )
        }
    }

    private fun countOccurrences(text: String, token: String): Int =
        Regex(Regex.escape(token)).findAll(text).count()
}
