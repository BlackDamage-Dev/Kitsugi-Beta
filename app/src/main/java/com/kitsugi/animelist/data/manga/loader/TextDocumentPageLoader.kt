package com.kitsugi.animelist.data.manga.loader

import com.kitsugi.animelist.data.manga.MangaPage
import com.kitsugi.animelist.data.manga.MangaPageStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** Bounded TXT/Markdown/FB2 reader sharing the novel renderer, progress and TTS pipeline. */
class TextDocumentPageLoader(private val file: File) {
    suspend fun loadPages(chapterUrl: String): List<MangaPage> = withContext(Dispatchers.IO) {
        require(file.length() in 1..MAX_TEXT_BYTES) { "Metin belgesi 16 MB sınırını aşıyor veya boş" }
        val text = decode(file.readBytes())
        val paragraphs = if (file.extension.equals("fb2", true)) parseFb2(text) else parsePlainText(text)
        paginate(paragraphs).mapIndexed { index, pageParagraphs ->
            MangaPage(index = index, url = "$chapterUrl#text-$index").apply {
                textParagraphs = pageParagraphs
                status = MangaPageStatus.Ready
            }
        }
    }

    private fun decode(bytes: ByteArray): String = try {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    } catch (_: CharacterCodingException) {
        String(bytes, charset("windows-1254"))
    }

    private fun parseFb2(xml: String): List<String> {
        val document = Jsoup.parse(xml, "", Parser.xmlParser())
        val body = document.select("body").firstOrNull { it.select("notes").isEmpty() } ?: document.selectFirst("body")
            ?: throw java.io.IOException("FB2 gövdesi bulunamadı")
        body.select("notes,comment,script,style,binary").remove()
        return body.select("section > title, section > subtitle, p, poem > title, v").eachText()
            .map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf(body.text().trim()).filter { it.isNotEmpty() } }
    }

    private fun parsePlainText(text: String): List<String> = text.replace("\r\n", "\n").replace('\r', '\n')
        .split(Regex("\\n[\\t ]*\\n+")).flatMap { block ->
            val trimmed = block.trim()
            if (trimmed.isEmpty() || '\n' in trimmed) trimmed.lines().map { it.trim() }.filter { it.isNotEmpty() }
            else listOf(trimmed)
        }.filter { it.isNotEmpty() }

    private fun paginate(paragraphs: List<String>): List<List<String>> {
        val pages = mutableListOf<List<String>>()
        var current = mutableListOf<String>()
        var length = 0
        fun flush() {
            if (current.isNotEmpty()) {
                pages += current.toList()
                current = mutableListOf()
                length = 0
            }
        }
        paragraphs.forEach { paragraph ->
            val chunks = paragraph.chunked(MAX_PAGE_CHARS).ifEmpty { listOf("") }
            chunks.forEach { chunk ->
                if (length + chunk.length > MAX_PAGE_CHARS) flush()
                current += chunk
                length += chunk.length + 2
            }
        }
        flush()
        return pages
    }

    companion object {
        const val MAX_TEXT_BYTES = 16L * 1024L * 1024L
        const val MAX_PAGE_CHARS = 6000
    }
}
