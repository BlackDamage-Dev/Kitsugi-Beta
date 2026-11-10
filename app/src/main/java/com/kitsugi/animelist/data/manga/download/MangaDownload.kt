package com.kitsugi.animelist.data.manga.download

import com.kitsugi.animelist.data.manga.ChapterRecognition
import com.kitsugi.animelist.data.manga.MangaChapter
import com.kitsugi.animelist.data.manga.MangaDetails
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest

@Serializable
enum class MangaDownloadStatus { Queued, Downloading, Paused, Error, Complete }

@Serializable
data class MangaDownloadedPage(
    val index: Int,
    val uri: String,
    val fileName: String,
    val mime: String,
    val bytes: Long = 0,
    val complete: Boolean = false
)

/** Durable manifest, not an evictable image cache or a list of signed CDN URLs. */
@Serializable
data class MangaDownload(
    val id: String,
    val sourceKey: String,
    val sourceName: String,
    val sourceLang: String,
    val mangaUrl: String,
    val mangaTitle: String,
    val chapterUrl: String,
    val chapterName: String,
    val chapterNumber: Float,
    val scanlator: String? = null,
    val relativePath: String,
    val workId: String = "",
    val status: MangaDownloadStatus = MangaDownloadStatus.Queued,
    val totalPages: Int = 0,
    val pages: List<MangaDownloadedPage> = emptyList(),
    val error: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
) {
    val hasAllPages: Boolean get() = totalPages > 0 && pages.size == totalPages &&
        pages.all { it.complete } && pages.map { it.index }.sorted() == (0 until totalPages).toList()
    val completedPages: Int get() = pages.count { it.complete }
    val sizeBytes: Long get() = pages.filter { it.complete }.sumOf { it.bytes }
    fun chapter() = MangaChapter(chapterUrl, chapterName, chapterNumber, scanlator, mangaUrl = mangaUrl)
    fun details() = MangaDetails(mangaUrl, mangaTitle, source = sourceName)
}

object MangaDownloadNaming {
    fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }.take(16)

    fun id(sourceKey: String, sourceName: String, lang: String, mangaUrl: String, chapterUrl: String) =
        digest(listOf(sourceKey, sourceName, lang, mangaUrl, chapterUrl).joinToString("\u0000"))

    /** Keep Unicode names, but bound UTF-8 bytes, not just UTF-16 characters. */
    fun safeSegment(value: String, maxBytes: Int = 80): String {
        val clean = value.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
            .trim().trim('.').ifBlank { "Manga" }
        val result = StringBuilder()
        var bytes = 0
        clean.codePoints().toArray().forEach { cp ->
            val char = String(Character.toChars(cp))
            val size = char.toByteArray(Charsets.UTF_8).size
            if (bytes + size <= maxBytes) { result.append(char); bytes += size }
        }
        return result.toString().trim().trim('.').ifBlank { "Manga" }
    }

    fun chapterLabel(title: String, name: String, number: Float): String {
        val n = ChapterRecognition.parseChapterNumber(title, name, number.toString().toDoubleOrNull())
        return if (n.isFinite() && n >= 0) "Bölüm_${BigDecimal.valueOf(n).stripTrailingZeros().toPlainString()}"
        else safeSegment(name, 48)
    }

    fun relativePath(sourceKey: String, source: String, title: String, mangaUrl: String, chapter: MangaChapter): String {
        val manga = "${safeSegment(title)}_${digest(mangaUrl).take(8)}"
        val n = ChapterRecognition.parseChapterNumber(title, chapter.name, chapter.chapterNumber.toString().toDoubleOrNull())
        val ordinal = if (n.isFinite() && n >= 0) BigDecimal.valueOf(n).setScale(6, RoundingMode.HALF_UP)
            .toPlainString().padStart(16, '0') else "zz_unknown"
        val folder = "${ordinal}_${safeSegment(title, 56)}_${chapterLabel(title, chapter.name, chapter.chapterNumber)}_${digest(chapter.url).take(8)}"
        return "Pictures/Kitsugi/Manga/${safeSegment(source, 48)}_${digest(sourceKey + "\u0000" + source).take(8)}/$manga/$folder/"
    }

    fun pageName(download: MangaDownload, index: Int, extension: String): String {
        require(index >= 0)
        require(extension in setOf("jpg", "png", "gif"))
        val number = (index + 1).toString().padStart(maxOf(4, download.totalPages.toString().length), '0')
        return "${safeSegment(download.mangaTitle)}_${chapterLabel(download.mangaTitle, download.chapterName, download.chapterNumber)}_Sayfa_$number.$extension"
    }
}
