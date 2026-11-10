package com.kitsugi.animelist.data.manga

import com.kitsugi.animelist.data.manga.loader.EpubPageLoader
import java.io.File

class LocalMangaArchiveSource(val archiveFile: File) : MangaSource {
    override val name: String = "Yerel Arşiv"
    override val lang: String = "all"
    override val baseUrl: String = archiveFile.toURI().toString()
    override val isArchive: Boolean = archiveFile.extension.lowercase() in setOf("zip", "cbz", "rar", "cbr")
    val isDocument: Boolean = archiveFile.extension.lowercase() in setOf("epub", "pdf", "txt", "md", "markdown", "fb2")

    override suspend fun fetchPopularManga(page: Int) = MangaSourceResult(listOf(details()), false)
    override suspend fun fetchSearchManga(page: Int, query: String) = MangaSourceResult(listOf(details()).filter { it.title.contains(query, true) }, false)
    override suspend fun fetchMangaDetails(mangaUrl: String): MangaDetails = details()

    override suspend fun fetchChapterList(mangaUrl: String): List<MangaChapter> {
        if (archiveFile.extension.equals("epub", ignoreCase = true)) {
            val toc = EpubPageLoader.extractToc(archiveFile)
            if (toc.isNotEmpty()) {
                return toc.mapIndexed { idx, item ->
                    MangaChapter(
                        url = "$baseUrl#${item.href}",
                        name = item.title.ifBlank { "Bölüm ${idx + 1}" },
                        chapterNumber = (idx + 1).toFloat(),
                        mangaUrl = baseUrl,
                    )
                }
            }
        }
        return listOf(MangaChapter(baseUrl, archiveFile.nameWithoutExtension, 1f, mangaUrl = baseUrl))
    }

    override suspend fun fetchPageList(chapter: MangaChapter): List<MangaPage> = emptyList()

    private fun details(): MangaDetails {
        val ext = archiveFile.extension.lowercase()
        val isNovelFormat = ext in setOf("epub", "txt", "md", "markdown", "fb2")
        val epubMeta = if (ext == "epub") EpubPageLoader.extractMetadata(archiveFile) else null

        val title = epubMeta?.title?.takeIf { it.isNotBlank() } ?: archiveFile.nameWithoutExtension
        val author = epubMeta?.author
        val description = epubMeta?.description

        return MangaDetails(
            url = baseUrl,
            title = title,
            source = name,
            author = author,
            description = description,
            genre = if (isNovelFormat) listOf("Novel", "Light Novel") else emptyList(),
        )
    }
}
