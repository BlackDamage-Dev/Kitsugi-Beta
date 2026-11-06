package com.kitsugi.animelist.ui.screens.manga

import com.kitsugi.animelist.data.manga.MangaChapter
import com.kitsugi.animelist.data.manga.MangaDetails
import com.kitsugi.animelist.data.manga.MangaPage
import com.kitsugi.animelist.data.manga.MangaSource
import com.kitsugi.animelist.data.manga.MangaSourceResult
import java.io.File
import java.io.IOException
import java.io.InputStream

/** Reuses the existing image loader, cache, settings and progress database. */
internal class LocalComicSource(
    private val archive: File,
    private val entries: List<LocalComicEntry>,
    private val identity: String,
    private val chapter: MangaChapter,
    private val details: MangaDetails,
) : MangaSource {
    override val name = "Local comic"
    override val baseUrl = "local-comic://reader"
    override val pkgName = "com.kitsugi.localcomic"
    override val lang = "all"
    private val byName = entries.associateBy { it.name }
    override suspend fun fetchPopularManga(page: Int) = MangaSourceResult(emptyList(), false)
    override suspend fun fetchSearchManga(page: Int, query: String) = MangaSourceResult(emptyList(), false)
    override suspend fun fetchMangaDetails(mangaUrl: String) = details
    override suspend fun fetchChapterList(mangaUrl: String) = listOf(chapter)
    override suspend fun fetchPageList(chapter: MangaChapter): List<MangaPage> = entries.mapIndexed { index, entry ->
        val key = readerSeriesId(identity, entry.name, entry.crc.toString(), entry.size.toString())
        MangaPage(index = index, url = entry.name, imageUrl = "local-comic://page/$key")
    }
    override suspend fun getImage(page: MangaPage): InputStream =
        LocalComicArchive.open(archive, byName[page.url] ?: throw IOException("Unknown page"))
}
