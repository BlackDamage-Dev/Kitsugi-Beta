package com.kitsugi.animelist.ui.screens.manga

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser

/** Extract readable text without loading external resources or interpreting FB2 as HTML. */
internal fun parseFictionBookText(bytes: ByteArray): List<String> {
    require(bytes.size <= 5 * 1024 * 1024) { "FB2 document is too large" }
    val doc = Jsoup.parse(bytes.inputStream(), null, "", Parser.xmlParser())
    fun Element.localName() = tagName().substringAfterLast(':').lowercase()
    val root = doc.children().firstOrNull { it.localName() == "fictionbook" }
        ?: throw IllegalArgumentException("Not a FictionBook document")
    val bodies = root.children().filter { it.localName() == "body" }
    require(bodies.isNotEmpty()) { "FictionBook has no body" }
    val blocks = setOf("p", "subtitle", "v", "text-author", "date", "empty-line")
    val lines = StringBuilder()
    var blockCount = 0
    for (body in bodies) {
        for (element in body.getAllElements()) {
            val name = element.localName()
            if (name !in blocks) continue
            // Never duplicate text contained by another emitted block or an embedded binary.
            var parent = element.parent()
            var skip = false
            while (parent != null && parent !== body) {
                if (parent.localName() in blocks || parent.localName() == "binary") { skip = true; break }
                parent = parent.parent()
            }
            if (skip) continue
            require(++blockCount <= 100_000) { "Too many FictionBook blocks" }
            if (blockCount > 1) lines.append('\n')
            if (name != "empty-line") lines.append(element.text().replace('\n', ' ').replace('\r', ' '))
        }
    }
    require(blockCount > 0 && lines.isNotBlank()) { "FictionBook has no readable text" }
    return splitNovelText(lines.toString())
}
