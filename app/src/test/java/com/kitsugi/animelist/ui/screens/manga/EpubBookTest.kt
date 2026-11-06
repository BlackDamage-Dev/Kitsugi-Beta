package com.kitsugi.animelist.ui.screens.manga

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubBookTest {
    @Test fun readsSpineOrderAndRemovesScripts() {
        val file = File.createTempFile("reader", ".epub")
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                fun entry(name: String, text: String) {
                    zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry()
                }
                entry("META-INF/container.xml", """<container><rootfiles><rootfile full-path="OPS/book.opf"/></rootfiles></container>""")
                entry("OPS/book.opf", """<package><manifest><item id="one" href="one.xhtml" media-type="application/xhtml+xml"/><item id="two" href="two.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="two"/><itemref idref="one"/></spine></package>""")
                entry("OPS/one.xhtml", "<html><body>First</body></html>")
                entry("OPS/two.xhtml", "<html><body>Second<script>alert(1)</script></body></html>")
            }
            EpubBook.open(file).use { book ->
                assertEquals(listOf("OPS/two.xhtml", "OPS/one.xhtml"), book.chapters)
                val html = book.chapterHtml(0, 20, 0)
                assertTrue(html.contains("Second"))
                assertFalse(html.contains("<script"))
                assertFalse(html.contains("text-align:justify"))
                assertTrue(book.chapterHtml(0, 20, 0, alignment = 2).contains("text-align:justify"))
                assertTrue(book.chapterHtml(0, 20, 0, hyphenation = true).contains("hyphens:auto"))
                assertFalse(book.chapterHtml(0, 20, 0).contains("hyphens:auto"))
                assertFalse(html.contains("letter-spacing:"))
                assertFalse(html.contains("margin-bottom:"))
                val styled = book.chapterHtml(0, 20, 0, letterSpacing = 50, paragraphGap = 99)
                assertTrue(styled.contains("letter-spacing:1.0px"))
                assertTrue(styled.contains("margin-bottom:24px"))
                val oled = book.chapterHtml(0, 99, 3, fontFamily = "Mono")
                assertTrue(oled.contains("background:#000000"))
                assertTrue(oled.contains("font-family:monospace"))
                assertTrue(oled.contains("font-size:48px"))
                assertTrue(book.chapterHtml(0, 10, 4).contains("background:#e5efdd"))
                assertTrue(book.chapterHtml(0, 20, 0, fontFamily = "evil;}").contains("font-family:serif"))
                val result = book.search("first")
                assertEquals(listOf(1), result.matches.map { it.chapter })
                assertTrue(result.matches.single().excerpt.contains("First"))
                assertTrue(book.search("alert(1)").matches.isEmpty())
                assertEquals(listOf("Second"), book.chapterSpeech(0))
                assertTrue(book.search("  ").matches.isEmpty())
            }
        } finally { file.delete() }
    }

    @Test fun readsEpub3NavigationLabelsInSpineOrder() {
        val file = File.createTempFile("reader-nav", ".epub")
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                fun entry(name: String, text: String) {
                    zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry()
                }
                entry("META-INF/container.xml", """<container><rootfile full-path="OPS/book.opf"/></container>""")
                entry("OPS/book.opf", """<package><manifest><item id="nav" href="nav.xhtml" properties="nav" media-type="application/xhtml+xml"/><item id="a" href="text/a.xhtml" media-type="application/xhtml+xml"/><item id="b" href="text/b.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="b"/><itemref idref="a"/></spine></package>""")
                entry("OPS/nav.xhtml", """<html xmlns:epub="http://www.idpf.org/2007/ops"><body><nav epub:type="toc"><ol><li><a href="text/a.xhtml#part">First</a><ol><li><a href="text/a.xhtml#part%202">First subsection</a></li><li><a href="https://example.org/out">External</a></li></ol></li><li><a href="text/b.xhtml">Second</a></li></ol></nav></body></html>""")
                entry("OPS/text/a.xhtml", "<html><body>A</body></html>")
                entry("OPS/text/b.xhtml", "<html><body>B</body></html>")
            }
            EpubBook.open(file).use { book ->
                assertEquals(listOf("Second", "First"), book.chapterTitles)
                assertEquals(listOf(
                    EpubTocEntry(1, "First", "part", 0),
                    EpubTocEntry(1, "First subsection", "part 2", 1),
                    EpubTocEntry(0, "Second", null, 0)
                ), book.tableOfContents)
            }
        } finally { file.delete() }
    }

    @Test fun readsEpub2NcxLabels() {
        val file = File.createTempFile("reader-ncx", ".epub")
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                fun entry(name: String, text: String) {
                    zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry()
                }
                entry("META-INF/container.xml", """<container><rootfile full-path="OPS/book.opf"/></container>""")
                entry("OPS/book.opf", """<package><manifest><item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/><item id="a" href="a.xhtml" media-type="application/xhtml+xml"/></manifest><spine toc="ncx"><itemref idref="a"/></spine></package>""")
                entry("OPS/toc.ncx", """<ncx><navMap><navPoint><navLabel><text>Opening</text></navLabel><content src="a.xhtml#start"/><navPoint><navLabel><text>Sub</text></navLabel><content src="a.xhtml#sub"/></navPoint></navPoint></navMap></ncx>""")
                entry("OPS/a.xhtml", "<html><body>A</body></html>")
            }
            EpubBook.open(file).use { book ->
                assertEquals(listOf("Opening"), book.chapterTitles)
                assertEquals(listOf(EpubTocEntry(0, "Opening", "start", 0), EpubTocEntry(0, "Sub", "sub", 1)), book.tableOfContents)
            }
        } finally { file.delete() }
    }

    @Test fun rejectsExternalReferences() {
        assertNull(EpubBook.resolveEpubPath("OPS/book.opf", "https://example.com/page.xhtml"))
        assertNull(EpubBook.resolveEpubPath("OPS/book.opf", "file:///etc/passwd"))
        assertNull(EpubBook.resolveEpubPath("OPS/book.opf", "%2e%2e/%2e%2e/private"))
        assertEquals("OPS/text/a.xhtml", EpubBook.resolveEpubPath("OPS/book.opf", "text/a.xhtml"))
        assertEquals("OPS/a.xhtml", EpubBook.resolveEpubPath("OPS/text/a.xhtml", "../a.xhtml"))
        assertEquals("OPS/a.xhtml", EpubBook.resolveEpubPath("OPS/text/a.xhtml", "../a.xhtml#part%20one"))
    }
    @Test fun bookSearchReturnsAllOccurrencesAndEnforcesBound() {
        val file = File.createTempFile("reader-search", ".epub")
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                fun entry(name: String, text: String) { zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry() }
                entry("META-INF/container.xml", """<container><rootfile full-path="book.opf"/></container>""")
                entry("book.opf", """<package><manifest><item id="a" href="a.xhtml" media-type="application/xhtml+xml"/><item id="b" href="b.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="a"/><itemref idref="b"/></spine></package>""")
                entry("a.xhtml", "<html><body>Apple and APPLE <script>apple</script> pear apple</body></html>")
                entry("b.xhtml", "<html><body>" + "apple ".repeat(110) + "</body></html>")
            }
            EpubBook.open(file).use { book ->
                val result = book.search("apple")
                assertEquals(100, result.matches.size)
                assertTrue(result.incomplete)
                assertEquals(listOf(0, 1, 2), result.matches.take(3).map { it.ordinal })
                assertTrue(result.matches.take(3).all { it.chapter == 0 })
                assertEquals(0, result.matches[3].ordinal)
                assertEquals(1, result.matches[3].chapter)
                var checks = 0
                try { book.search("apple") { if (++checks == 2) throw IllegalStateException("cancelled") }; throw AssertionError("Expected cancellation") }
                catch (e: IllegalStateException) { assertEquals("cancelled", e.message) }
            }
        } finally { file.delete() }
    }
}
