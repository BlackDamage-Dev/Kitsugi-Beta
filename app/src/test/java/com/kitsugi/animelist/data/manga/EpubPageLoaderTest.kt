package com.kitsugi.animelist.data.manga

import com.kitsugi.animelist.data.manga.loader.EpubPageLoader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun extractsOpfMetadataAndCover() {
        val epub = temporary.newFile("meta.epub")
        ZipOutputStream(epub.outputStream()).use { zip ->
            fun entry(name: String, content: ByteArray) {
                zip.putNextEntry(ZipEntry(name)); zip.write(content); zip.closeEntry()
            }
            entry("META-INF/container.xml", """<container><rootfiles><rootfile full-path="content.opf"/></rootfiles></container>""".toByteArray())
            entry("cover.jpg", byteArrayOf(1, 2, 3, 4))
            entry("content.opf", """<package>
                <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>Test Light Novel</dc:title>
                    <dc:creator>Test Author</dc:creator>
                    <dc:description>A great fantasy light novel.</dc:description>
                    <dc:language>tr</dc:language>
                    <meta name="cover" content="cover-image"/>
                </metadata>
                <manifest>
                    <item id="cover-image" href="cover.jpg" media-type="image/jpeg"/>
                </manifest>
                <spine></spine>
            </package>""".toByteArray())
        }
        val meta = EpubPageLoader.extractMetadata(epub)
        assertNotNull(meta)
        assertEquals("Test Light Novel", meta?.title)
        assertEquals("Test Author", meta?.author)
        assertEquals("A great fantasy light novel.", meta?.description)
        assertEquals("tr", meta?.language)
        assertEquals("cover.jpg", meta?.coverHref)
    }

    @Test
    fun extractsEpub2NcxToc() {
        val epub = temporary.newFile("ncx.epub")
        ZipOutputStream(epub.outputStream()).use { zip ->
            fun entry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name)); zip.write(content.toByteArray()); zip.closeEntry()
            }
            entry("META-INF/container.xml", """<container><rootfiles><rootfile full-path="OEBPS/content.opf"/></rootfiles></container>""")
            entry("OEBPS/toc.ncx", """<ncx>
                <navMap>
                    <navPoint playOrder="1"><navLabel><text>Prologue</text></navLabel><content src="text/prologue.xhtml"/></navPoint>
                    <navPoint playOrder="2"><navLabel><text>Chapter 1</text></navLabel><content src="text/chap1.xhtml"/></navPoint>
                </navMap>
            </ncx>""")
            entry("OEBPS/content.opf", """<package>
                <manifest>
                    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                    <item id="c1" href="text/prologue.xhtml" media-type="application/xhtml+xml"/>
                    <item id="c2" href="text/chap1.xhtml" media-type="application/xhtml+xml"/>
                </manifest>
                <spine><itemref idref="c1"/><itemref idref="c2"/></spine>
            </package>""")
        }
        val toc = EpubPageLoader.extractToc(epub)
        assertEquals(2, toc.size)
        assertEquals("Prologue", toc[0].title)
        assertEquals("OEBPS/text/prologue.xhtml", toc[0].href)
        assertEquals("Chapter 1", toc[1].title)
        assertEquals("OEBPS/text/chap1.xhtml", toc[1].href)
    }

    @Test
    fun extractsEpub3NavDocumentToc() {
        val epub = temporary.newFile("nav.epub")
        ZipOutputStream(epub.outputStream()).use { zip ->
            fun entry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name)); zip.write(content.toByteArray()); zip.closeEntry()
            }
            entry("META-INF/container.xml", """<container><rootfiles><rootfile full-path="EPUB/package.opf"/></rootfiles></container>""")
            entry("EPUB/nav.xhtml", """<html xmlns:epub="http://www.idpf.org/2007/ops"><body>
                <nav epub:type="toc">
                    <ol>
                        <li><a href="c1.xhtml">Episode 1</a></li>
                        <li><a href="c2.xhtml">Episode 2</a></li>
                    </ol>
                </nav></body></html>""")
            entry("EPUB/package.opf", """<package>
                <manifest>
                    <item id="nav" href="nav.xhtml" properties="nav" media-type="application/xhtml+xml"/>
                    <item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/>
                    <item id="c2" href="c2.xhtml" media-type="application/xhtml+xml"/>
                </manifest>
                <spine><itemref idref="c1"/><itemref idref="c2"/></spine>
            </package>""")
        }
        val toc = EpubPageLoader.extractToc(epub)
        assertEquals(2, toc.size)
        assertEquals("Episode 1", toc[0].title)
        assertEquals("EPUB/c1.xhtml", toc[0].href)
        assertEquals("Episode 2", toc[1].title)
        assertEquals("EPUB/c2.xhtml", toc[1].href)
    }

    @Test
    fun pathTraversalAttemptsAreSafelyRejected() {
        assertNull(EpubPageLoader.resolveArchivePath("OPS", "../../secret.txt"))
        assertNull(EpubPageLoader.resolveArchivePath("", "../secret.txt"))
        assertEquals("OPS/image.png", EpubPageLoader.resolveArchivePath("OPS/sub", "../image.png"))
    }

    @Test
    fun imageOnlyEpubLoadsAsImagePages() = runBlocking {
        val epub = temporary.newFile("manga.epub")
        ZipOutputStream(epub.outputStream()).use { zip ->
            fun entry(name: String, content: ByteArray) {
                zip.putNextEntry(ZipEntry(name)); zip.write(content); zip.closeEntry()
            }
            entry("META-INF/container.xml", """<container><rootfiles><rootfile full-path="package.opf"/></rootfiles></container>""".toByteArray())
            entry("page1.jpg", byteArrayOf(1, 2, 3))
            entry("page1.xhtml", """<html><body><img src="page1.jpg"/></body></html>""".toByteArray())
            entry("package.opf", """<package>
                <manifest>
                    <item id="p1" href="page1.xhtml" media-type="application/xhtml+xml"/>
                    <item id="img1" href="page1.jpg" media-type="image/jpeg"/>
                </manifest>
                <spine><itemref idref="p1"/></spine>
            </package>""".toByteArray())
        }
        val pages = EpubPageLoader(epub).loadPages("local://manga")
        assertEquals(1, pages.size)
        assertNull(pages[0].textParagraphs) // Image-only page!
        assertTrue(pages[0].imageUrl?.startsWith("file://") == true)
        assertEquals(MangaPageStatus.Ready, pages[0].status)
    }
}
