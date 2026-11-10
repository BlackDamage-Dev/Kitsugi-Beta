package com.kitsugi.animelist.data.manga

import com.kitsugi.animelist.data.manga.loader.TextDocumentPageLoader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TextDocumentPageLoaderTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun plainTextIsPaginatedWithoutExceedingPageLimit() = runBlocking {
        val file = temporary.newFile("book.txt")
        file.writeText("First paragraph.\n\n" + "Long paragraph. ".repeat(1000))
        val pages = TextDocumentPageLoader(file).loadPages("local://text")
        assertTrue(pages.size > 1)
        assertTrue(pages.all { it.textParagraphs.orEmpty().sumOf(String::length) <= TextDocumentPageLoader.MAX_PAGE_CHARS })
        assertEquals("First paragraph.", pages.first().textParagraphs?.first())
        assertEquals((pages.indices).toList(), pages.map { it.index })
    }

    @Test
    fun fb2KeepsReadingContentAndDropsNotesAndBinary() = runBlocking {
        val file = temporary.newFile("book.fb2")
        file.writeText("""<FictionBook><body><section><title>Chapter</title><p>Readable text</p>
            <notes><p>Footnote only</p></notes><binary>base64</binary></section></body></FictionBook>""")
        val pages = TextDocumentPageLoader(file).loadPages("local://fb2")
        val text = pages.flatMap { it.textParagraphs.orEmpty() }.joinToString(" ")
        assertTrue("Chapter" in text && "Readable text" in text)
        assertFalse("Footnote only" in text || "base64" in text)
    }

    @Test
    fun invalidUtf8UsesTurkishWindowsEncoding() = runBlocking {
        val file = temporary.newFile("turkish.txt")
        file.writeBytes("ŞİĞÇÖÜışğçöü".toByteArray(charset("windows-1254")))
        val pages = TextDocumentPageLoader(file).loadPages("local://encoding")
        assertEquals("ŞİĞÇÖÜışğçöü", pages.single().textParagraphs?.single())
    }
}
