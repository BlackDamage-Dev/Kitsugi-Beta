package com.kitsugi.animelist.data.manga.loader

import com.kitsugi.animelist.data.manga.MangaPage
import com.kitsugi.animelist.data.manga.MangaPageStatus
import com.kitsugi.animelist.data.manga.MangaTextParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File
import java.net.URI
import java.util.zip.ZipFile

/** EPUB 2/3 spine reader. OPF order is authoritative; directory entry order is never used. */
class EpubPageLoader(private val file: File) {
    suspend fun loadPages(chapterUrl: String): List<MangaPage> = withContext(Dispatchers.IO) {
        ZipFile(file).use { zip ->
            val container = zip.read("META-INF/container.xml")
            val rootfile = Jsoup.parse(container, "", Parser.xmlParser())
                .selectFirst("rootfile")?.attr("full-path")
                ?: throw java.io.IOException("EPUB container.xml içinde OPF bulunamadı")
            val opf = Jsoup.parse(zip.read(rootfile), "", Parser.xmlParser())
            val manifest = opf.select("manifest > item").associateBy { it.attr("id") }
            val base = rootfile.substringBeforeLast('/', "")
            val pages = opf.select("spine > itemref").mapNotNull { spine ->
                val item = manifest[spine.attr("idref")] ?: return@mapNotNull null
                resolveArchivePath(base, item.attr("href"))?.takeIf { zip.getEntry(it) != null }
            }
            if (pages.isEmpty()) throw java.io.IOException("EPUB spine bölümü boş")
            pages.mapIndexedNotNull { index, path ->
                val html = zip.read(path)
                val paragraphs = MangaTextParser.parse(html)
                if (paragraphs.isEmpty()) return@mapIndexedNotNull null
                MangaPage(index = index, url = "$chapterUrl#epub-$index", imageUrl = "$chapterUrl#epub-$index").apply {
                    textParagraphs = paragraphs
                    status = MangaPageStatus.Ready
                }
            }.mapIndexed { index, page -> MangaPage(index = index, url = page.url, imageUrl = page.imageUrl).apply {
                textParagraphs = page.textParagraphs
                status = MangaPageStatus.Ready
            } }
        }
    }

    private fun ZipFile.read(path: String): String = (getEntry(path) ?: throw java.io.FileNotFoundException(path))
        .let { entry -> getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() } }

    private fun resolveArchivePath(base: String, href: String): String? = runCatching {
        val decoded = URI(href.substringBefore('#')).path ?: return null
        val parts = (if (decoded.startsWith('/')) decoded.drop(1) else "$base/$decoded").split('/')
        val result = mutableListOf<String>()
        parts.forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> if (result.isNotEmpty()) result.removeAt(result.lastIndex) else return null
                else -> result += part
            }
        }
        result.joinToString("/")
    }.getOrNull()
}
