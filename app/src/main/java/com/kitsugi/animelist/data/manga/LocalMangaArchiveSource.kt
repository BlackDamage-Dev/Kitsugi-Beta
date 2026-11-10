package com.kitsugi.animelist.data.manga

import java.io.File

class LocalMangaArchiveSource(val archiveFile: File) : MangaSource {
    override val name: String = "Yerel Arşiv"
    override val lang: String = "all"
    override val baseUrl: String = archiveFile.toURI().toString()
    override val isArchive: Boolean = true
    override suspend fun fetchPopularManga(page: Int) = MangaSourceResult(listOf(details()), false)
    override suspend fun fetchSearchManga(page: Int, query: String) = MangaSourceResult(listOf(details()).filter { it.title.contains(query, true) }, false)
    override suspend fun fetchMangaDetails(mangaUrl: String): MangaDetails = details()
    override suspend fun fetchChapterList(mangaUrl: String) = listOf(MangaChapter(baseUrl, archiveFile.nameWithoutExtension, 1f, mangaUrl = baseUrl))
    override suspend fun fetchPageList(chapter: MangaChapter): List<MangaPage> = emptyList()
    private fun details() = MangaDetails(baseUrl, archiveFile.nameWithoutExtension, source = "Yerel Arşiv")
}
