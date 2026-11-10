package com.kitsugi.animelist.data.manga

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MangaTextParserTest {
    @Test
    fun extractsChapterBodyAndRemovesUiAndExecutableMarkup() {
        val html = """
            <html><body><nav>Previous chapter</nav><article>
            <h1>Chapter 4</h1><p>First paragraph.</p><p>Second &amp; final paragraph.</p>
            <script>tracker()</script><form><button>Subscribe</button></form>
            </article><footer>Advertisement</footer></body></html>
        """.trimIndent()
        assertEquals(listOf("Chapter 4", "First paragraph.", "Second & final paragraph."), MangaTextParser.parse(html))
    }

    @Test
    fun fallsBackToPlainBodyText() {
        val parsed = MangaTextParser.parse("<html><body>A short chapter without paragraph tags.</body></html>")
        assertEquals(listOf("A short chapter without paragraph tags."), parsed)
        assertFalse(parsed.any { "html" in it })
    }
}
