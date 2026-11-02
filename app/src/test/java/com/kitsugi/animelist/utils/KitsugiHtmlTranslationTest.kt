package com.kitsugi.animelist.utils

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Biyografi HTML'i otomatik çeviri sözleşmesi.
 *
 * Kullanıcı biyografileri HTML gelir (AniList `<br>`, `<a>`, `<img>` kullanır). Çeviri
 * yalnızca metin düğümlerine dokunmalı; etiketler, bağlantı adresleri ve görseller
 * aynen korunmalıdır. Aksi hâlde çeviri sonrası biyografi bozulur.
 */
class KitsugiHtmlTranslationTest {

    @Test
    fun `text segments are extracted in document order and tags are ignored`() {
        val html = "<p>Hello world</p><a href=\"https://x.y\">Link text</a>"

        val segments = KitsugiHtmlTranslation.extractTextSegments(html)

        assertEquals(listOf("Hello world", "Link text"), segments)
    }

    @Test
    fun `script style and code contents are never translated`() {
        val html = "<p>About me</p><script>var bio = 'hello world';</script>" +
            "<style>.bio { color: red; }</style><code>fun main() = print(\"hi there\")</code>"

        val segments = KitsugiHtmlTranslation.extractTextSegments(html)

        assertEquals(listOf("About me"), segments)
    }

    @Test
    fun `short punctuation urls and entities are skipped`() {
        val html = "<br><p>1</p><p>&nbsp;</p><p>https://example.com/a-b</p><p>Real bio text</p>"

        val segments = KitsugiHtmlTranslation.extractTextSegments(html)

        assertEquals(listOf("Real bio text"), segments)
    }

    @Test
    fun `rebuild keeps tags and replaces only the translated text nodes`() {
        val html = "<p>Hello world</p><a href=\"https://x.y\">Link text</a><img src=\"a.png\">"

        val rebuilt = KitsugiHtmlTranslation.rebuild(html, listOf("Merhaba dünya", "Bağlantı yazısı"))

        assertEquals(
            "<p>Merhaba dünya</p><a href=\"https://x.y\">Bağlantı yazısı</a><img src=\"a.png\">",
            rebuilt
        )
    }

    @Test
    fun `rebuild keeps the original node when its translation is missing`() {
        val html = "<p>Hello world</p><p>Second sentence</p>"

        val rebuilt = KitsugiHtmlTranslation.rebuild(html, listOf("Merhaba dünya", null))

        assertEquals("<p>Merhaba dünya</p><p>Second sentence</p>", rebuilt)
    }

    @Test
    fun `translateHtml returns translated markup`() = runBlocking {
        val html = "<p>Hello world</p><br><p>Second sentence</p>"

        val result = KitsugiHtmlTranslation.translateHtml(html) { segment ->
            segment.uppercase()
        }

        assertEquals("<p>HELLO WORLD</p><br><p>SECOND SENTENCE</p>", result)
    }

    @Test
    fun `translateHtml returns null when the provider never answers`() = runBlocking {
        val html = "<p>Hello world</p><p>Second sentence</p>"

        val result = KitsugiHtmlTranslation.translateHtml(html) { null }

        assertNull(result)
    }

    @Test
    fun `translateHtml keeps markup when only some nodes fail`() = runBlocking {
        val html = "<p>Hello world</p><p>Second sentence</p>"

        val result = KitsugiHtmlTranslation.translateHtml(html) { segment ->
            if (segment.startsWith("Hello")) "Merhaba dünya" else null
        }

        assertEquals("<p>Merhaba dünya</p><p>Second sentence</p>", result)
    }

    @Test
    fun `biography without any translatable text yields null`() = runBlocking {
        val result = KitsugiHtmlTranslation.translateHtml("<br><img src=\"a.png\">") { it }

        assertNull(result)
        assertTrue(KitsugiHtmlTranslation.extractTextSegments("").isEmpty())
    }
}
