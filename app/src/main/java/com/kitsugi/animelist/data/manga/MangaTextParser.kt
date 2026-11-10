package com.kitsugi.animelist.data.manga

import org.jsoup.Jsoup

/** Converts source XHTML/HTML into readable paragraphs without navigation, scripts or tracking markup. */
object MangaTextParser {
    private val contentSelectors = listOf(
        "article", ".chapter-content", "#chapter-content", ".chapter_content",
        ".entry-content", ".text-left", ".novel-content", "main", "body",
    )

    fun parse(html: String): List<String> {
        val document = Jsoup.parse(html)
        document.select("script,style,noscript,nav,header,footer,form,iframe,button,.advertisement,.ads").remove()
        val root = contentSelectors.asSequence().mapNotNull(document::selectFirst).firstOrNull() ?: document.body()
        val paragraphs = root.select("h1,h2,h3,h4,p,li,blockquote").eachText()
            .map { it.trim() }.filter { it.isNotEmpty() }
        if (paragraphs.isNotEmpty()) return paragraphs
        return root.text().split(Regex("\\n+")).map { it.trim() }.filter { it.isNotEmpty() }
    }
}
