package com.kitsugi.animelist.data.manga

import com.kitsugi.animelist.data.manga.loader.EpubPageLoader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubPageLoaderTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun followsOpfSpineAndNormalizesRelativeChapterPaths() = runBlocking {
        val epub = temporary.newFile("book.epub")
        ZipOutputStream(epub.outputStream()).use { zip ->
            fun entry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name)); zip.write(content.toByteArray()); zip.closeEntry()
            }
            entry("META-INF/container.xml", """<container><rootfiles><rootfile full-path="OPS/package.opf"/></rootfiles></container>""")
            entry("text/z-last.xhtml", "<html><body><p>Second spine chapter</p></body></html>")
            entry("text/a-first.xhtml", "<html><body><h1>Start</h1><p>First spine chapter</p></body></html>")
            entry("OPS/package.opf", """<package><manifest>
                <item id="second" href="text/z-last.xhtml" media-type="application/xhtml+xml"/>
                <item id="first" href="text/a-first.xhtml" media-type="application/xhtml+xml"/>
                </manifest><spine><itemref idref="first"/><itemref idref="second"/></spine></package>""")
        }
        val pages = EpubPageLoader(epub).loadPages("local://book")
        assertEquals(2, pages.size)
        assertEquals(listOf("Start", "First spine chapter"), pages[0].textParagraphs)
        assertEquals(listOf("Second spine chapter"), pages[1].textParagraphs)
        assertEquals(listOf(0, 1), pages.map { it.index })
    }
}
