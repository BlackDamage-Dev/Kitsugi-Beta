package com.kitsugi.animelist.ui.screens.manga

import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File
import java.io.IOException
import java.net.URI
import java.util.zip.ZipFile

/** EPUB container/spine access without extracting entries to the filesystem. */
internal class EpubBook private constructor(
    private val zip: ZipFile,
    val chapters: List<String>,
    val chapterTitles: List<String?>,
    val tableOfContents: List<EpubTocEntry>,
) : AutoCloseable {
    companion object {
        private const val MAX_XML = 2 * 1024 * 1024
        private const val MAX_ENTRIES = 10000
        private const val ORIGIN = "https://epub.kitsugi.invalid/"

        fun open(file: File): EpubBook {
            val zip = ZipFile(file)
            try {
                require(zip.size() <= MAX_ENTRIES) { "Too many EPUB entries" }
                fun xml(path: String): org.jsoup.nodes.Document {
                    val entry = zip.getEntry(path) ?: throw IOException("Missing EPUB entry")
                    val bytes = zip.getInputStream(entry).use { readBoundedNovel(it, MAX_XML) }
                    return Jsoup.parse(bytes.inputStream(), null, "", Parser.xmlParser())
                }
                val container = xml("META-INF/container.xml")
                val root = container.getAllElements().firstOrNull { it.tagName().substringAfterLast(':') == "rootfile" }?.attr("full-path")
                    ?.takeIf { it.isNotBlank() } ?: throw IOException("Missing EPUB package")
                val rootPath = resolveEpubPath("", root) ?: throw IOException("Invalid package path")
                val packageDoc = xml(rootPath)
                val manifestNode = packageDoc.getAllElements().firstOrNull { it.tagName().substringAfterLast(':') == "manifest" }
                    ?: throw IOException("Missing manifest")
                val spineNode = packageDoc.getAllElements().firstOrNull { it.tagName().substringAfterLast(':') == "spine" }
                    ?: throw IOException("Missing spine")
                val manifest = manifestNode.children().filter { it.tagName().substringAfterLast(':') == "item" }.associate { element ->
                    element.attr("id") to (element.attr("href") to element.attr("media-type"))
                }
                val chapters = spineNode.children().filter { it.tagName().substringAfterLast(':') == "itemref" }.mapNotNull { item ->
                    if (item.attr("linear") == "no") return@mapNotNull null
                    val (href, type) = manifest[item.attr("idref")] ?: return@mapNotNull null
                    if (type != "application/xhtml+xml" && type != "text/html") return@mapNotNull null
                    resolveEpubPath(rootPath, href)?.takeIf { zip.getEntry(it) != null }
                }
                require(chapters.isNotEmpty()) { "No readable chapters" }
                val titles = mutableMapOf<String, String>()
                val chapterIndexes = mutableMapOf<String, Int>()
                chapters.forEachIndexed { index, path -> chapterIndexes.putIfAbsent(path, index) }
                val navEntries = mutableListOf<EpubTocEntry>()
                val ncxEntries = mutableListOf<EpubTocEntry>()
                fun addToc(entries: MutableList<EpubTocEntry>, base: String, reference: String, label: String, depth: Int) {
                    if (entries.size >= 5000 || label.isBlank()) return
                    val path = resolveEpubPath(base, reference) ?: return
                    val index = chapterIndexes[path] ?: return
                    val fragment = try { URI(reference).fragment?.takeIf { it.isNotEmpty() && it.length <= 2048 } }
                        catch (_: Exception) { null }
                    val title = label.trim().take(200)
                    entries.add(EpubTocEntry(index, title, fragment, depth.coerceIn(0, 8)))
                    titles.putIfAbsent(path, title)
                }
                // EPUB 3 navigation document and EPUB 2 NCX are optional. A broken
                // navigation file must not prevent reading an otherwise valid book.
                try {
                    val navItem = manifestNode.children().firstOrNull { item ->
                        item.attr("properties").split(Regex("\\s+")).contains("nav")
                    }
                    val navPath = navItem?.attr("href")?.let { resolveEpubPath(rootPath, it) }
                    if (navPath != null) {
                        val nav = xml(navPath)
                        val toc = nav.getAllElements().firstOrNull { it.tagName().substringAfterLast(':') == "nav" &&
                            (it.attr("epub:type").split(' ').contains("toc") || it.attr("role") == "doc-toc") }
                        (toc ?: nav).select("a[href]").forEach { anchor ->
                            val depth = (anchor.parents().count { it.tagName().substringAfterLast(':') == "li" } - 1).coerceAtLeast(0)
                            addToc(navEntries, navPath, anchor.attr("href"), anchor.text(), depth)
                        }
                    }
                } catch (_: Exception) { /* Use NCX or numbered chapters. */ }
                try {
                    val ncxHref = manifest[spineNode.attr("toc")]?.first
                        ?: manifest.values.firstOrNull { it.second == "application/x-dtbncx+xml" }?.first
                    val ncxPath = ncxHref?.let { resolveEpubPath(rootPath, it) }
                    if (ncxPath != null) {
                        val ncx = xml(ncxPath)
                        ncx.getAllElements().filter { it.tagName().substringAfterLast(':') == "navPoint" }.forEach { point ->
                            val content = point.children().firstOrNull { it.tagName().substringAfterLast(':') == "content" }
                            val label = point.children().firstOrNull { it.tagName().substringAfterLast(':') == "navLabel" }?.text()?.trim()
                            val depth = point.parents().count { it.tagName().substringAfterLast(':') == "navPoint" }
                            if (content != null && label != null) addToc(ncxEntries, ncxPath, content.attr("src"), label, depth)
                        }
                    }
                } catch (_: Exception) { /* Numbered chapters remain available. */ }
                return EpubBook(zip, chapters, chapters.map { titles[it] }, navEntries.ifEmpty { ncxEntries })
            } catch (e: Exception) {
                zip.close()
                throw e
            }
        }

        /** Resolve only URLs inside our synthetic origin; no network or filesystem access. */
        internal fun resolveEpubPath(basePath: String, reference: String): String? = try {
            val base = URI(ORIGIN).resolve(URI(null, null, basePath, null))
            val resolved = base.resolve(reference).normalize()
            val path = resolved.path?.removePrefix("/").orEmpty()
            if (resolved.scheme != "https" || resolved.host != "epub.kitsugi.invalid" ||
                path.isEmpty() || path.split('/').any { it == ".." || it == "." } || '\\' in path) null
            else path
        } catch (_: Exception) { null }
    }

    fun read(path: String, limit: Int = 5 * 1024 * 1024): ByteArray? {
        val entry = zip.getEntry(path) ?: return null
        return zip.getInputStream(entry).use { readBoundedNovel(it, limit) }
    }

    /** Book-wide search is bounded to avoid inflating arbitrarily large archives. */
    fun search(query: String, checkCancelled: () -> Unit = {}): EpubSearchResults {
        val needle = query.trim().take(100)
        if (needle.isEmpty()) return EpubSearchResults(emptyList(), false)
        val matches = mutableListOf<EpubSearchMatch>()
        var remaining = 32 * 1024 * 1024
        var incomplete = false
        for ((index, path) in chapters.withIndex()) {
            checkCancelled()
            if (remaining <= 0 || matches.size >= 100) { incomplete = true; break }
            val limit = minOf(remaining, 5 * 1024 * 1024)
            val bytes = try { read(path, limit) } catch (_: IOException) {
                remaining -= limit
                incomplete = true
                continue
            } catch (_: IllegalArgumentException) {
                remaining -= limit
                incomplete = true
                continue
            }
            if (bytes == null) { incomplete = true; continue }
            remaining -= bytes.size
            val document = Jsoup.parse(bytes.inputStream(), null, "")
            document.select("script, style, noscript, template").remove()
            val text = document.body().text()
            var position = 0
            var ordinal = 0
            while (position < text.length) {
                checkCancelled()
                val at = text.indexOf(needle, startIndex = position, ignoreCase = true)
                if (at < 0) break
                if (matches.size >= 100) { incomplete = true; break }
                val from = (at - 60).coerceAtLeast(0)
                val to = (at + needle.length + 100).coerceAtMost(text.length)
                matches.add(EpubSearchMatch(index, (if (from > 0) "…" else "") +
                    text.substring(from, to) + (if (to < text.length) "…" else ""), ordinal))
                ordinal++
                position = at + needle.length
            }
            if (incomplete && matches.size >= 100) break
        }
        return EpubSearchResults(matches, incomplete)
    }

    /** Extract the current chapter's textual content for foreground speech. */
    fun chapterSpeech(index: Int): List<String> {
        val bytes = read(chapters[index], 5 * 1024 * 1024) ?: throw IOException("Missing chapter")
        val doc = Jsoup.parse(bytes.inputStream(), null, "")
        doc.select("script, style, noscript, template, iframe, object, embed, form").remove()
        return splitSpeechText(doc.body().text())
    }

    /** Inline scripts, navigation redirects and network loads are not needed for reading. */
    fun chapterHtml(index: Int, fontSize: Int, theme: Int, lineHeight: Int = 150, margin: Int = 24, serif: Boolean = true, alignment: Int = 0, letterSpacing: Int = 0, paragraphGap: Int = -1, fontFamily: String? = null, hyphenation: Boolean = false): String {
        val bytes = read(chapters[index], 5 * 1024 * 1024) ?: throw IOException("Missing chapter")
        val doc = Jsoup.parse(bytes.inputStream(), null, "")
        doc.select("script, iframe, object, embed, form, base, meta[http-equiv]").remove()
        val head = doc.head()
        head.prependElement("meta").attr("http-equiv", "Content-Security-Policy")
            .attr("content", "default-src 'none'; img-src https://epub.kitsugi.invalid data:; style-src 'unsafe-inline' https://epub.kitsugi.invalid; font-src https://epub.kitsugi.invalid; media-src 'none'")
        val bg = when (theme) { 1 -> "#f4ecd8"; 2 -> "#ffffff"; 3 -> "#000000"; 4 -> "#e5efdd"; else -> "#11121a" }
        val fg = if (theme == 1 || theme == 2 || theme == 4) "#25211e" else "#f3f1f7"
        head.appendElement("meta").attr("name", "viewport").attr("content", "width=device-width, initial-scale=1")
        val family = when (fontFamily) { "Mono" -> "monospace"; "Sans" -> "sans-serif"; "Serif" -> "serif"; else -> if (serif) "serif" else "sans-serif" }
        head.appendElement("style").text("html,body { background:$bg !important; color:$fg !important; font-size:${fontSize.coerceIn(10, 48)}px !important; line-height:${lineHeight.coerceIn(120, 200)}% !important; font-family:$family !important; overflow-wrap:anywhere; } body { margin: ${margin.coerceIn(12, 40)}px !important; } img,svg { max-width:100%; height:auto; }")
        val align = when (alignment) { 1 -> "start"; 2 -> "justify"; else -> null }
        if (align != null) head.appendElement("style").text("body, p { text-align:$align !important; }")
        if (letterSpacing > 0) head.appendElement("style").text("body { letter-spacing:${letterSpacing.coerceIn(0, 10) / 10f}px !important; }")
        if (paragraphGap >= 0) head.appendElement("style").text("p { margin-top:0 !important; margin-bottom:${paragraphGap.coerceIn(0, 24)}px !important; }")
        if (hyphenation) head.appendElement("style").text("body, p { -webkit-hyphens:auto !important; hyphens:auto !important; }")
        return doc.outerHtml()
    }

    override fun close() = zip.close()
}

internal data class EpubSearchMatch(val chapter: Int, val excerpt: String, val ordinal: Int = 0)
internal data class EpubSearchResults(val matches: List<EpubSearchMatch>, val incomplete: Boolean)

/** Entries retain navigation order, hierarchy and fragment, independently of spine order. */
internal data class EpubTocEntry(val chapter: Int, val title: String, val fragment: String?, val depth: Int)
